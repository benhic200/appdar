#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# check_osm_brands.sh — Appdar OSM brand tag validator
#
# Reads every brand map from NearbyBranchFinder.kt, queries the Overpass API
# for each tag within its region bounding box, and reports tags that return
# 0 results (likely wrong spelling, capitalisation, or apostrophe encoding).
#
# Usage:
#   cd /path/to/phase1
#   ./scripts/check_osm_brands.sh           # check all regions
#   ./scripts/check_osm_brands.sh UK        # check only UK_BRANDS
#   ./scripts/check_osm_brands.sh AU NZ     # check AU and NZ
#
# Available region names: UK US AU NZ GLOBAL
#
# Requirements: curl, jq
# ─────────────────────────────────────────────────────────────────────────────

FINDER="data/src/main/kotlin/com/benhic/appdar/data/nearby/NearbyBranchFinder.kt"
OVERPASS="https://overpass-api.de/api/interpreter"

# Bounding boxes — must match Region enum in NearbyBranchFinder.kt
declare -A BBOX=(
    [UK]="49.5,-11.0,61.0,2.0"
    [US]="24.0,-125.0,49.5,-66.0"
    [AU]="-43.6,113.3,-10.0,153.6"
    [NZ]="-47.4,166.4,-34.4,178.6"
    [GLOBAL]="49.5,-11.0,61.0,2.0"   # use UK bbox as a representative sample
)

# Region to check GLOBAL brands in — UK is a reasonable proxy
GLOBAL_CHECK_REGION="UK"

# ── Colour helpers ────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

# ── Arg parsing: which regions to check ──────────────────────────────────────
if [[ $# -eq 0 ]]; then
    REGIONS_TO_CHECK=("UK" "US" "AU" "NZ" "GLOBAL")
else
    REGIONS_TO_CHECK=("$@")
fi

if [[ ! -f "$FINDER" ]]; then
    echo "Error: $FINDER not found. Run from the phase1/ project root." >&2
    exit 1
fi

command -v jq >/dev/null || { echo "Error: jq not found. Install with: apt install jq" >&2; exit 1; }

# ── Extract brand map from Kotlin source ─────────────────────────────────────
# Returns lines of "business_name|osm_tag" for a given map name (e.g. UK_BRANDS)
# Uses split() on double-quote delimiters — compatible with mawk and gawk.
extract_brand_map() {
    local map_name="$1"
    awk -v MAP="private val ${map_name} = mapOf" '
        $0 ~ MAP         { inside=1; next }
        inside && /^        \)/ { inside=0; next }
        inside {
            n = split($0, a, "\"")
            if (n >= 4) print a[2] "|" a[4]
        }
    ' "$FINDER"
}

# ── Query Overpass ────────────────────────────────────────────────────────────
query_brand_count() {
    local osm_tag="$1"
    local bbox="$2"
    # Escape double quotes in the tag
    local safe_tag="${osm_tag//\"/\\\"}"
    local query="[out:json][timeout:30];(nwr[\"brand\"=\"${safe_tag}\"](${bbox}););out count;"

    local response
    response=$(curl -s --max-time 35 \
        --data-urlencode "data=${query}" \
        "$OVERPASS" 2>/dev/null)

    if [[ -z "$response" ]]; then
        echo "-1"
        return
    fi

    # Overpass count response: {"elements":[{"type":"count","tags":{"nodes":"5","ways":"3",...}}]}
    local nodes ways rels total
    nodes=$(echo "$response" | jq -r '.elements[0].tags.nodes // "0"' 2>/dev/null)
    ways=$(echo  "$response" | jq -r '.elements[0].tags.ways  // "0"' 2>/dev/null)
    rels=$(echo  "$response" | jq -r '.elements[0].tags.relations // "0"' 2>/dev/null)

    # Sum all element types
    echo $(( ${nodes:-0} + ${ways:-0} + ${rels:-0} ))
}

# ── Main loop ─────────────────────────────────────────────────────────────────
declare -a ZERO_RESULTS=()
total_ok=0; total_zero=0; total_error=0

for region in "${REGIONS_TO_CHECK[@]}"; do
    map_name="${region}_BRANDS"
    bbox="${BBOX[$region]}"

    if [[ -z "$bbox" ]]; then
        echo "Unknown region: $region (valid: UK US AU NZ GLOBAL)" >&2
        continue
    fi

    echo ""
    echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}  ${map_name} — querying within ${region} bbox (${bbox})${NC}"
    echo -e "${CYAN}══════════════════════════════════════════════════════════════${NC}"
    echo ""

    mapfile -t PAIRS < <(extract_brand_map "$map_name")

    if [[ ${#PAIRS[@]} -eq 0 ]]; then
        echo -e "  ${YELLOW}⚠  Could not parse ${map_name} from source file${NC}"
        continue
    fi

    printf "  %-8s  %-28s  %s\n" "COUNT" "BUSINESS NAME" "OSM BRAND TAG"
    printf "  %-8s  %-28s  %s\n" "────────" "────────────────────────────" "──────────────────────────────"

    for pair in "${PAIRS[@]}"; do
        biz="${pair%%|*}"
        tag="${pair##*|}"

        count=$(query_brand_count "$tag" "$bbox")

        if [[ "$count" -eq -1 ]]; then
            printf "  ${YELLOW}%-8s${NC}  %-28s  \"%s\"\n" "ERROR" "$biz" "$tag"
            ((total_error++))
        elif [[ "$count" -eq 0 ]]; then
            printf "  ${RED}%-8s${NC}  %-28s  \"%s\"\n" "✗ ZERO" "$biz" "$tag"
            ZERO_RESULTS+=("${region}|${biz}|${tag}")
            ((total_zero++))
        else
            printf "  ${GREEN}%-8s${NC}  %-28s  \"%s\"\n" "✓ $count" "$biz" "$tag"
            ((total_ok++))
        fi

        # Overpass asks for ~1 req/s from public endpoints
        sleep 1.2
    done
done

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════════════════════════"
echo "  Results: $total_ok ok  |  $total_zero zero  |  $total_error errors"
echo ""

if [[ ${#ZERO_RESULTS[@]} -gt 0 ]]; then
    echo -e "${RED}Brand tags with 0 OSM results — likely wrong tag:${NC}"
    echo ""
    for entry in "${ZERO_RESULTS[@]}"; do
        r="${entry%%|*}";  rest="${entry#*|}"; biz="${rest%%|*}"; tag="${rest##*|}"
        echo "  [${r}] ${biz}"
        echo "        Current tag:  \"${tag}\""
        echo "        Check at:     https://www.openstreetmap.org/search?query=${tag// /+}"
        echo "        Overpass:     https://overpass-turbo.eu/#  (paste query below)"
        echo "        Query:        nwr[\"brand\"=\"${tag}\"](${BBOX[$r]});out;"
        echo ""
    done
    echo "  Tip: try common variations — extra spaces, different apostrophes ('"
    echo "  vs '), full vs short name (e.g. 'JB Hi-Fi' vs 'JB HiFi')."
else
    echo -e "  ${GREEN}✓ All brand tags returned at least one OSM result.${NC}"
fi

echo ""
