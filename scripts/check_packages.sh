#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# check_packages.sh — Appdar Play Store package name validator
#
# Reads every createMapping(...) entry from InitialDataset.kt and checks
# whether the package name resolves on the Google Play Store.
#
# For any package not found, searches the Play Store for suggestions and
# offers an interactive prompt to fix the package name in InitialDataset.kt.
#
# Usage:
#   cd /path/to/phase1
#   ./scripts/check_packages.sh [--fast] [--no-interactive]
#--Region UK includes the "US-origin brands with UK presence" section (Costco, Taco Bell, Chipotle etc.) because that comment contains both "UK" and "US"
#--Region US also picks those up for the same reason — so they're covered by both
#The flag is case-insensitive: --Region uk, --region AU, --Region=NZ all work
#./scripts/check_packages.sh --Region UK    # UK sections + Global (Hilton, BP, Shell)
#./scripts/check_packages.sh --Region US    # US sections + Global
#./scripts/check_packages.sh --Region AU    # Australia section only
#./scripts/check_packages.sh --Region NZ    # New Zealand section only
#./scripts/check_packages.sh --Region Global # Global-only sections
#./scripts/check_packages.sh               # all regions (original behaviour)
# Flags:
#   --fast            Skip the 0.4s delay between checks (may get rate-limited)
#   --no-interactive  Print missing list only; skip the interactive fix step
#   --Region <name>   Only check entries in the given region.
#                     Valid values: UK, US, AU, NZ, Global
#                     UK and US automatically include Global sections too.
#
# Notes:
#   - Region-locked apps (e.g. AU/NZ) may return 404 when checked from a
#     non-AU/NZ server. This is expected — verify those manually.
#   - HTTP 429 = rate limited. Slow down or check manually.
# ─────────────────────────────────────────────────────────────────────────────

DATASET="data/src/main/kotlin/com/benhic/appdar/data/local/InitialDataset.kt"
PLAY_URL="https://play.google.com/store/apps/details"
PLAY_SEARCH="https://play.google.com/store/search"
DELAY=0.4
INTERACTIVE=true
REGION=""

# ── Parse flags ───────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --fast)           DELAY=0; shift ;;
        --no-interactive) INTERACTIVE=false; shift ;;
        --Region|--region)
            [[ -n "$2" && "$2" != --* ]] || { echo "Error: --Region requires a value (e.g. --Region UK)" >&2; exit 1; }
            REGION="${2^^}"; shift 2 ;;
        --Region=*|--region=*)
            REGION="${1#*=}"; REGION="${REGION^^}"; shift ;;
        *) echo "Unknown flag: $1" >&2; exit 1 ;;
    esac
done

# ── Validate region ───────────────────────────────────────────────────────────
if [[ -n "$REGION" ]]; then
    case "$REGION" in
        UK|US|AU|AUSTRALIA|NZ|GLOBAL) ;;
        *) echo "Error: Unknown region '$REGION'. Valid: UK, US, AU, NZ, Global" >&2; exit 1 ;;
    esac
fi

if [[ ! -f "$DATASET" ]]; then
    echo "Error: $DATASET not found. Run from the phase1/ project root." >&2
    exit 1
fi

# ── Extract mappings: business_name TAB package_name TAB section ─────────────
# Uses split() on double-quote delimiters — compatible with mawk and gawk.
# Section is the most-recent "// ──" comment, used for region filtering.
mapfile -t ALL_ENTRIES < <(
    awk '
        /\/\/ ──/ { section = $0 }
        /createMapping\(/ {
            n = split($0, a, "\"")
            if (n >= 4) print a[2] "\t" a[4] "\t" section
        }
    ' "$DATASET"
)

# ── Filter by region ──────────────────────────────────────────────────────────
# UK/US also include Global sections (Hilton, BP, Shell, etc.)
# US-origin brands with UK presence are tagged for both UK and US.
ENTRIES=()
for _entry in "${ALL_ENTRIES[@]}"; do
    _business="${_entry%%	*}"
    _rest="${_entry#*	}"
    _package="${_rest%%	*}"
    _section="${_entry##*	}"
    _sec_up="${_section^^}"

    _include=false
    if [[ -z "$REGION" ]]; then
        _include=true
    else
        case "$REGION" in
            UK)              [[ "$_sec_up" == *UK* || "$_sec_up" == *GLOBAL* ]] && _include=true ;;
            US)              [[ "$_sec_up" == *US* || "$_sec_up" == *GLOBAL* ]] && _include=true ;;
            AU|AUSTRALIA)    [[ "$_sec_up" == *AU* ]]                   && _include=true ;;
            NZ)              [[ "$_sec_up" == *"NZ"* ]]               && _include=true ;;
            GLOBAL)          [[ "$_sec_up" == *GLOBAL* ]]                      && _include=true ;;
        esac
    fi

    [[ "$_include" == true ]] && ENTRIES+=("${_business}	${_package}")
done

# ── Colour helpers ────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

found=0; not_found=0; errors=0; skipped=0
declare -a MISSING=()

echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
if [[ -n "$REGION" ]]; then
    printf "║          Appdar — Play Store Package Validator  [Region: %-4s]       ║\n" "$REGION"
else
    echo "║          Appdar — Play Store Package Validator  [All Regions]        ║"
fi
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""
printf "%-8s  %-50s  %s\n" "STATUS" "PACKAGE" "BUSINESS"
printf "%-8s  %-50s  %s\n" "────────" "──────────────────────────────────────────────────" "────────────────────────"

for entry in "${ENTRIES[@]}"; do
    business="${entry%%	*}"
    package="${entry##*	}"
    [[ -z "$package" || "$package" == "$business" ]] && continue

    http_code=$(curl -s -o /dev/null -w "%{http_code}" \
        --max-time 12 \
        --location \
        -H "User-Agent: Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36" \
        -H "Accept-Language: en-GB,en;q=0.9" \
        "${PLAY_URL}?id=${package}&hl=en")

    case "$http_code" in
        200)
            printf "${GREEN}%-8s${NC}  %-50s  %s\n" "✓ FOUND" "$package" "$business"
            ((found++))
            ;;
        404)
            printf "${RED}%-8s${NC}  %-50s  %s\n" "✗ MISS" "$package" "$business"
            ((not_found++))
            MISSING+=("$business|$package")
            ;;
        429)
            printf "${YELLOW}%-8s${NC}  %-50s  %s\n" "LIMIT" "$package" "$business"
            ((errors++))
            echo "  ⚠  Rate limited — waiting 30s..."
            sleep 30
            ;;
        000)
            printf "${YELLOW}%-8s${NC}  %-50s  %s\n" "TIMEOUT" "$package" "$business"
            ((errors++))
            ;;
        *)
            printf "${YELLOW}%-8s${NC}  %-50s  %s\n" "HTTP $http_code" "$package" "$business"
            ((errors++))
            ;;
    esac

    [[ "$DELAY" != "0" ]] && sleep "$DELAY"
done

# ── Summary ───────────────────────────────────────────────────────────────────
total=$((found + not_found + errors + skipped))
echo ""
echo "────────────────────────────────────────────────────────────────────────"
echo "  Total: $total  |  Found: $found  |  Not found: $not_found  |  Errors: $errors"
echo ""

if [[ ${#MISSING[@]} -eq 0 ]]; then
    echo -e "${GREEN}✓ All packages found on the Play Store.${NC}"
    echo ""
    exit 0
fi

echo -e "${RED}Packages NOT found on Play Store:${NC}"
for entry in "${MISSING[@]}"; do
    biz="${entry%%|*}"
    pkg="${entry##*|}"
    echo "  • $biz"
    echo "      Package: $pkg"
    echo "      URL:     https://play.google.com/store/apps/details?id=$pkg"
done
echo ""
echo "  Tip: region-locked apps (AU, NZ) may return 404 from a UK/EU server."
echo ""

[[ "$INTERACTIVE" == false ]] && exit 0

# ── Helper: escape string for a sed search pattern (using | as delimiter) ────
sed_escape_search() {
    printf '%s' "$1" | sed 's/[\\|.*+?^${}()\[]/\\&/g'
}

# ── Helper: escape string for a sed replacement (using | as delimiter) ───────
sed_escape_replace() {
    printf '%s' "$1" | sed 's/[\\|&]/\\&/g'
}

# ── Search Play Store and return up to 5 candidate package names ──────────────
search_suggestions() {
    local query="$1"
    local encoded="${query// /+}"
    local html
    html=$(curl -s --max-time 15 \
        -H "User-Agent: Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Mobile Safari/537.36" \
        -H "Accept-Language: en-GB,en;q=0.9" \
        "${PLAY_SEARCH}?q=${encoded}&c=apps&hl=en" 2>/dev/null)

    # Extract package IDs from href links, deduplicate preserving page order, return first 5
    echo "$html" \
        | grep -oP '(?<=details\?id=)[^"& ]+' \
        | grep -v '^$' \
        | awk '!seen[$0]++' \
        | head -5
}

# ── Remove a brand entry entirely from InitialDataset.kt ─────────────────────
remove_brand() {
    local business="$1"
    local esc_biz
    esc_biz=$(sed_escape_search "$business")

    if sed -i "/createMapping(\"${esc_biz}\",[[:space:]]*/d" "$DATASET"; then
        echo -e "  ${GREEN}✓ Removed${NC}  \"${business}\" deleted from $DATASET"
    else
        echo -e "  ${RED}✗ sed failed${NC} — edit $DATASET manually"
    fi
}

# ── Rewrite package name in InitialDataset.kt ─────────────────────────────────
update_package() {
    local business="$1"
    local old_pkg="$2"
    local new_pkg="$3"

    local esc_biz esc_old esc_new
    esc_biz=$(sed_escape_search "$business")
    esc_old=$(sed_escape_search "$old_pkg")
    esc_new=$(sed_escape_replace "$new_pkg")

    if sed -i "s|createMapping(\"${esc_biz}\",[[:space:]]*\"${esc_old}\"|createMapping(\"${esc_biz}\", \"${esc_new}\"|" "$DATASET"; then
        echo -e "  ${GREEN}✓ Updated${NC}  \"${business}\"  ${old_pkg}  →  ${new_pkg}"
    else
        echo -e "  ${RED}✗ sed failed${NC} — edit $DATASET manually"
    fi
}

# ── Interactive fix loop ──────────────────────────────────────────────────────
echo -e "${CYAN}╔══════════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║          Interactive Fix — searching Play Store for matches          ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════════════╝${NC}"
echo ""

for entry in "${MISSING[@]}"; do
    biz="${entry%%|*}"
    pkg="${entry##*|}"

    echo -e "${YELLOW}▶  ${biz}${NC}"
    echo "   Current package: ${pkg}"
    echo "   Searching Play Store..."

    mapfile -t SUGGESTIONS < <(search_suggestions "$biz")

    if [[ ${#SUGGESTIONS[@]} -eq 0 ]]; then
        echo "   No suggestions found via search."
    else
        echo "   Suggestions:"
        for i in "${!SUGGESTIONS[@]}"; do
            printf "     %d) %s\n" "$((i+1))" "${SUGGESTIONS[$i]}"
            printf "        https://play.google.com/store/apps/details?id=%s\n" "${SUGGESTIONS[$i]}"
        done
    fi

    echo ""

    while true; do
        if [[ ${#SUGGESTIONS[@]} -gt 0 ]]; then
            echo "   [Y] use suggestion 1  |  [1-${#SUGGESTIONS[@]}] pick a suggestion  |  [N] enter manually  |  [D] delete brand  |  [S] skip"
        else
            echo "   [N] enter correct package name  |  [D] delete brand  |  [S] skip"
        fi
        read -r -p "   > " choice

        if [[ "$choice" =~ ^[Ss]$ || -z "$choice" ]]; then
            echo "   Skipped."
            break

        elif [[ "$choice" =~ ^[Dd]$ ]]; then
            read -r -p "   Delete \"${biz}\" from InitialDataset.kt? [y/N] " confirm
            if [[ "$confirm" =~ ^[Yy]$ ]]; then
                remove_brand "$biz"
            else
                echo "   Cancelled."
                continue
            fi
            break

        elif [[ "$choice" =~ ^[Yy]$ ]] && [[ ${#SUGGESTIONS[@]} -gt 0 ]]; then
            update_package "$biz" "$pkg" "${SUGGESTIONS[0]}"
            pkg="${SUGGESTIONS[0]}"
            break

        elif [[ "$choice" =~ ^[1-9][0-9]*$ ]] && [[ "$choice" -le "${#SUGGESTIONS[@]}" ]]; then
            update_package "$biz" "$pkg" "${SUGGESTIONS[$((choice-1))]}"
            pkg="${SUGGESTIONS[$((choice-1))]}"
            break

        elif [[ "$choice" =~ ^[Nn]$ ]]; then
            read -r -p "   Enter correct package name: " manual
            if [[ -n "$manual" ]]; then
                update_package "$biz" "$pkg" "$manual"
                pkg="$manual"
            else
                echo "   Empty input — skipped."
            fi
            break

        else
            echo "   Unrecognised input — try again."
        fi
    done

    echo ""
done

echo "────────────────────────────────────────────────────────────────────────"
echo "  Interactive fix complete. Re-run this script to verify changes."
echo ""
