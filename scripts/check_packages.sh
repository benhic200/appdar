#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# check_packages.sh — Appdar Play Store package name validator
#
# Reads every createMapping(...) entry from InitialDataset.kt and checks
# whether the package name resolves on the Google Play Store.
#
# Usage:
#   cd /path/to/phase1
#   ./scripts/check_packages.sh [--region AU|NZ|UK|US]
#
# Flags:
#   --region <R>  Only check entries whose category comment region matches R
#   --fast        Skip the 0.4s delay between requests (may get rate-limited)
#
# Notes:
#   - Region-locked apps (e.g. AU/NZ) may return 404 when checked from a
#     non-AU/NZ server. This is expected — verify those manually.
#   - HTTP 429 = rate limited. Slow down or check manually.
# ─────────────────────────────────────────────────────────────────────────────

DATASET="data/src/main/kotlin/com/benhic/appdar/data/local/InitialDataset.kt"
PLAY_URL="https://play.google.com/store/apps/details"
DELAY=0.4
FILTER_REGION=""

# ── Parse flags ───────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --region) FILTER_REGION="$2"; shift 2 ;;
        --fast)   DELAY=0; shift ;;
        *) echo "Unknown flag: $1" >&2; exit 1 ;;
    esac
done

if [[ ! -f "$DATASET" ]]; then
    echo "Error: $DATASET not found. Run from the phase1/ project root." >&2
    exit 1
fi

# ── Extract mappings: business_name TAB package_name ─────────────────────────
# Uses split() on double-quote delimiters — compatible with mawk and gawk.
# Matches lines like:
#   createMapping("Woolworths", "com.woolworths", "Woolworths", "supermarket", ...),
mapfile -t ENTRIES < <(
    awk '
        /createMapping\(/ {
            n = split($0, a, "\"")
            if (n >= 4) print a[2] "\t" a[4]
        }
    ' "$DATASET"
)

# ── Colour helpers ────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'

found=0; not_found=0; errors=0; skipped=0
declare -a MISSING=()

echo ""
echo "╔══════════════════════════════════════════════════════════════════════╗"
echo "║          Appdar — Play Store Package Validator                       ║"
echo "╚══════════════════════════════════════════════════════════════════════╝"
echo ""
printf "%-8s  %-50s  %s\n" "STATUS" "PACKAGE" "BUSINESS"
printf "%-8s  %-50s  %s\n" "────────" "──────────────────────────────────────────────────" "────────────────────────"

for entry in "${ENTRIES[@]}"; do
    business="${entry%%	*}"
    package="${entry##*	}"
    [[ -z "$package" || "$package" == "$business" ]] && continue

    # Check package
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

if [[ ${#MISSING[@]} -gt 0 ]]; then
    echo -e "${RED}Packages NOT found on Play Store (verify manually — may be region-locked):${NC}"
    for entry in "${MISSING[@]}"; do
        biz="${entry%%|*}"
        pkg="${entry##*|}"
        echo "  • $biz"
        echo "      Package: $pkg"
        echo "      URL:     https://play.google.com/store/apps/details?id=$pkg"
    done
    echo ""
    echo "  Tip: region-locked apps (AU, NZ) may return 404 from a UK/EU server."
    echo "  Check these at: https://play.google.com/store/apps/details?id=<package>"
fi

echo ""
