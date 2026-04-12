package com.benhic.appdar.data.local

/**
 * Initial hard-coded dataset of business chains, organised by region.
 *
 * regionHint rules:
 *   null   = visible in every region (truly global app — one package works everywhere)
 *   "UK"   = shown only to UK users
 *   "US"   = shown only to US users
 *   "AU"   = shown only to AU users
 *   "NZ"   = shown only to NZ users
 *
 * Every region has its own entry for global brands (McDonald's, KFC, etc.) because
 * each region uses a different app package. Global brands with a single worldwide
 * app (Starbucks, Subway, IKEA …) use regionHint = null.
 *
 * Package names marked "// verify package" should be checked before shipping —
 * they are best-guesses for regions not yet live-tested.
 */
object InitialDataset {
    fun getMappings(): List<BusinessAppMapping> = listOf(

        // ── UK Supermarkets ───────────────────────────────────────────────────
        createMapping("Tesco", "com.tesco.grocery.view", "Tesco", "supermarket", regionHint = "UK"),
        createMapping("Sainsbury's",  "com.ga.loyalty.android.nectar.activities",  "Sainsbury's Nectar","supermarket", regionHint = "UK"),
        createMapping("Asda",         "com.asda.android",                          "Asda Groceries",    "supermarket", regionHint = "UK"),
        createMapping("Morrisons",    "com.morrisons.atm.mobile.android",          "Morrisons Grocery", "supermarket", regionHint = "UK"),
        createMapping("Aldi",         "de.apptiv.business.android.aldi_uk",        "Aldi Offers",       "supermarket", regionHint = "UK"),
        createMapping("Lidl",         "com.lidl.eci.lidlplus",                     "Lidl Plus",         "supermarket", regionHint = "UK"),
        createMapping("Waitrose",     "com.waitrose.groceries",                    "Waitrose",          "supermarket", regionHint = "UK"),
        createMapping("M&S",          "com.marksandspencer.app",                   "M&S",               "supermarket", regionHint = "UK"),
        createMapping("Iceland",      "com.iceland.android",                       "Iceland",           "supermarket", regionHint = "UK"),
        createMapping("Co-op",        "uk.co.coop.app",                            "Co-op",             "supermarket", regionHint = "UK"),

        // ── UK Coffee & Bakery ────────────────────────────────────────────────
        createMapping("Costa Coffee",  "uk.co.club.costa.costa",   "Costa Coffee",  "coffee", regionHint = "UK"),
        createMapping("Caffè Nero",    "com.yoyowallet.caffenero", "Caffè Nero",    "coffee", regionHint = "UK"),
        createMapping("Pret A Manger", "com.pret.android",         "Pret A Manger", "coffee", regionHint = "UK"),
        createMapping("Greggs",        "com.mobile5.greggs",       "Greggs",        "bakery", regionHint = "UK"),

        // ── UK Fast Food ──────────────────────────────────────────────────────
        createMapping("McDonald's",  "com.mcdonalds.app.uk",            "McDonald's",  "fast_food", regionHint = "UK"),
        createMapping("Burger King", "com.emn8.mobilem8.nativeapp.bkuk","Burger King", "fast_food", regionHint = "UK"),
        createMapping("KFC",         "com.yum.colonelsclub",            "KFC",         "fast_food", regionHint = "UK"),
        createMapping("Domino's",    "uk.co.dominos.android",           "Domino's",    "fast_food", regionHint = "UK"),
        createMapping("Pizza Hut",   "com.pizzahutuk.orderingApp",      "Pizza Hut",   "fast_food", regionHint = "UK"),
        createMapping("Nando's",     "nandos.android.app",              "Nando's",     "fast_food", regionHint = "UK"),
        createMapping("Five Guys",   "com.fiveguys.fiveguysuk",         "Five Guys",   "fast_food", regionHint = "UK"),
        createMapping("Wagamama",    "com.wagamama.soulclubapp",        "wagamama",    "fast_food", regionHint = "UK"),
        createMapping("Papa John's", "com.papajohns.android",           "Papa John's", "fast_food", regionHint = "UK"),

        // ── UK Casual Dining / Pubs ───────────────────────────────────────────
        createMapping("Wetherspoons",  "com.wetherspoon.orderandpay", "Wetherspoons",  "pub",           regionHint = "UK"),
        createMapping("Pizza Express", "com.pizzaexpress.appv2",      "Pizza Express", "casual_dining", regionHint = "UK"),
        createMapping("Zizzi",         "com.azzurri.zizziloyalty",    "Zizzi",         "casual_dining", regionHint = "UK"),
        createMapping("Yo! Sushi",     "com.upmenu.yoSushi",          "YO! Sushi",     "casual_dining", regionHint = "UK"),
        createMapping("TGI Fridays",   "com.punchh.tgifuk",           "TGI Fridays",   "casual_dining", regionHint = "UK"),

        // ── UK Pharmacy & Retail ──────────────────────────────────────────────
        createMapping("Boots",         "com.boots.flagship.android",      "Boots",         "pharmacy", regionHint = "UK"),
        createMapping("Superdrug",     "superdrug.com.beautycard",        "Superdrug",     "pharmacy", regionHint = "UK"),
        createMapping("WHSmith",       "com.whsmith.mywhsmith.android",   "WHSmith",       "retail",   regionHint = "UK"),
        createMapping("Argos",         "com.homeretailgroup.argos.android","Argos",         "retail",   regionHint = "UK"),
        createMapping("Next",          "uk.co.next.android",              "Next",          "retail",   regionHint = "UK"),
        createMapping("JD Sports",     "com.jd.jdsports",                 "JD Sports",     "retail",   regionHint = "UK"),
        createMapping("Sports Direct", "com.sportsdirect.sdapp",          "Sports Direct", "retail",   regionHint = "UK"),

        // ── UK Cinema ─────────────────────────────────────────────────────────
        createMapping("Odeon",     "nz.co.vista.android.movie.odeoncinemas", "Odeon",      "entertainment", regionHint = "UK"),
        createMapping("Vue",       "com.myvue.app",                          "Vue Cinema", "entertainment", regionHint = "UK"),
        createMapping("Cineworld", "com.cineworld.app",                      "Cineworld",  "entertainment", regionHint = "UK"),

        // ── UK Hotels ─────────────────────────────────────────────────────────
        createMapping("Premier Inn", "com.whitbread.premierinn", "Premier Inn", "hotel", regionHint = "UK"),
        createMapping("Travelodge",  "co.uk.travelodge.app",     "Travelodge",  "hotel", regionHint = "UK"),

        // ── Global (single app works in all regions) ──────────────────────────
        createMapping("Starbucks",   "com.starbucks.mobilecard",      "Starbucks",       "coffee"),
        createMapping("Subway",      "com.subway.mobile.subwayapp03", "Subway",          "fast_food"),
        createMapping("IKEA",        "com.ingka.ikea.app",            "IKEA",            "retail"),
        createMapping("Hilton",      "com.hilton.android.hhonors",    "Hilton Honors",   "hotel"),
        createMapping("Marriott",    "com.marriott.mrt",              "Marriott Bonvoy", "hotel"),
        createMapping("Holiday Inn", "com.ihg.apps.android",          "IHG Hotels",      "hotel"),
        createMapping("BP",          "com.bp.mobile.bpme.uk",         "BPme",            "fuel"),
        createMapping("Shell",       "com.shell.sitibv.retail",       "Shell Go+",       "fuel"),
        createMapping("Costco",      "intl.costco.com.mobile.uk",     "Costco",          "supermarket"),
        createMapping("Taco Bell",   "com.tacobelluk.app",            "Taco Bell",       "fast_food"),
        createMapping("Chipotle",    "com.chipotle.ordering.eu",      "Chipotle",        "fast_food"),
        createMapping("Shake Shack", "com.thesweetshakeshack",        "Shake Shack",     "fast_food"),

        // ── United States ─────────────────────────────────────────────────────

        // ── US Supermarkets ───────────────────────────────────────────────────
        createMapping("Walmart",      "com.walmart.android",                    "Walmart",         "supermarket", isEnabled = false, regionHint = "US"),
        createMapping("Target",       "com.target.ui",                          "Target",          "retail",      isEnabled = false, regionHint = "US"),
        createMapping("Kroger",       "com.kroger.mobile",                      "Kroger",          "supermarket", isEnabled = false, regionHint = "US"),
        createMapping("Safeway",      "com.safeway.client.android.safeway",     "Safeway",         "supermarket", isEnabled = false, regionHint = "US"),
        createMapping("Albertsons",   "com.safeway.client.android.albertsons",  "Albertsons",      "supermarket", isEnabled = false, regionHint = "US"),
        createMapping("Publix",       "com.publix.main",                        "Publix",          "supermarket", isEnabled = false, regionHint = "US"),

        // ── US Coffee & Bakery ────────────────────────────────────────────────
        createMapping("Dunkin'",      "com.dunkind.app",                        "Dunkin'",         "coffee",      isEnabled = false, regionHint = "US"),

        // ── US Pharmacy ───────────────────────────────────────────────────────
        createMapping("CVS",          "com.cvs.launchers.cvs",                  "CVS Pharmacy",    "pharmacy",    isEnabled = false, regionHint = "US"),
        createMapping("Walgreens",    "com.usablenet.mobile.walgreen",          "Walgreens",       "pharmacy",    isEnabled = false, regionHint = "US"),

        // ── US Fast Food ──────────────────────────────────────────────────────
        createMapping("McDonald's",   "com.mcdonalds.app",                      "McDonald's",      "fast_food",   isEnabled = false, regionHint = "US"),
        createMapping("Burger King",  "com.emn8.mobilem8.nativeapp.bk",         "Burger King",     "fast_food",   isEnabled = false, regionHint = "US,NZ"), // same app used in NZ
        createMapping("KFC",          "com.kfc.us.mobile",                      "KFC",             "fast_food",   isEnabled = false, regionHint = "US"),
        createMapping("Chick-fil-A",  "com.chickfila.cfaflagship",              "Chick-fil-A",     "fast_food",   isEnabled = false, regionHint = "US"),
        createMapping("Wendy's",      "com.wendys.nutritiontool",               "Wendy's",         "fast_food",   isEnabled = false, regionHint = "US"),
        createMapping("Panera Bread", "com.panera.bread",                       "Panera Bread",    "fast_food",   isEnabled = false, regionHint = "US"),
        createMapping("Domino's",     "com.dominospizza",                       "Domino's",        "fast_food",   isEnabled = false, regionHint = "US"),
        createMapping("Pizza Hut",    "com.yum.pizzahut",                       "Pizza Hut",       "fast_food",   isEnabled = false, regionHint = "US"),

        // ── US Cinema ─────────────────────────────────────────────────────────
        createMapping("AMC Theatres", "com.amc",                                "AMC Theatres",    "entertainment", isEnabled = false, regionHint = "US"),
        createMapping("Regal",        "com.fandango.regal",                     "Regal",           "entertainment", isEnabled = false, regionHint = "US"),

        // ── Australia ─────────────────────────────────────────────────────────

        // ── AU Supermarkets ───────────────────────────────────────────────────
        createMapping("Woolworths",        "com.woolworths",                         "Woolworths",         "supermarket", isEnabled = false, regionHint = "AU"),
        createMapping("Coles",             "com.coles.android.shopmate",             "Coles",              "supermarket", isEnabled = false, regionHint = "AU"),
        createMapping("Aldi", "de.apptiv.business.android.aldi_au", "Aldi", "supermarket", isEnabled = false, regionHint = "AU"),

        // ── AU Pharmacy & Retail ──────────────────────────────────────────────
        createMapping("Chemist Warehouse", "au.com.cwretailservices.cwapp",          "Chemist Warehouse",  "pharmacy",    isEnabled = false, regionHint = "AU"),
        createMapping("Dan Murphy's",      "au.com.danmurphys",                      "Dan Murphy's",       "retail",      isEnabled = false, regionHint = "AU"),
        createMapping("Bunnings",          "com.bunnings.retail",                    "Bunnings",           "retail",      isEnabled = false, regionHint = "AU"),
        createMapping("Myer", "myer.com.android", "Myer", "retail", isEnabled = false, regionHint = "AU"),

        // ── AU Fast Food ──────────────────────────────────────────────────────
        createMapping("McDonald's",        "com.mcdonalds.au.gma",                   "MyMacca's",          "fast_food",   isEnabled = false, regionHint = "AU"),
        createMapping("Hungry Jack's",     "com.webling.hungryjacks",                "Hungry Jack's",      "fast_food",   isEnabled = false, regionHint = "AU"),
        createMapping("KFC", "com.kfcaus.ordering", "KFC", "fast_food", isEnabled = false, regionHint = "AU")
        createMapping("Domino's",          "au.com.dominos.olo.android.app",         "Domino's",           "fast_food",   isEnabled = false, regionHint = "AU"),
        createMapping("Pizza Hut", "com.pizzahutau", "Pizza Hut", "fast_food", isEnabled = false, regionHint = "AU")

        // ── AU Cinema ─────────────────────────────────────────────────────────
        createMapping("Event Cinemas",     "com.ahl.eventcinemas",                   "Event Cinemas",      "entertainment", isEnabled = false, regionHint = "AU"),
        createMapping("Hoyts",             "nz.co.vista.android.movie",              "HOYTS",              "entertainment", isEnabled = false, regionHint = "AU"),

        // ── New Zealand ───────────────────────────────────────────────────────

        // ── NZ Supermarkets ───────────────────────────────────────────────────
        createMapping("Woolworths NZ", "nz.co.countdown.android.pickup",        "Woolworths NZ",      "supermarket", isEnabled = false, regionHint = "NZ"),
        createMapping("New World",     "nz.co.newworld.clubcard",               "New World",          "supermarket", isEnabled = false, regionHint = "NZ"),

        // ── NZ Pharmacy & Retail ──────────────────────────────────────────────
        createMapping("The Warehouse", "nz.co.thewarehouse.wow",                "The Warehouse",      "retail",      isEnabled = false, regionHint = "NZ"),
        createMapping("Bunnings", "com.bunnings.retail", "Bunnings", "retail", isEnabled = false, regionHint = "NZ"),

        // ── NZ Fuel ───────────────────────────────────────────────────────────
        createMapping("Z",             "com.zenergy.zenergyapp.android.prod",   "Z App",              "fuel",        isEnabled = false, regionHint = "NZ"),

        // ── NZ Fast Food ──────────────────────────────────────────────────────
        createMapping("McDonald's", "com.mcdonalds.mobileapp", "McDonald's", "fast_food", isEnabled = false, regionHint = "NZ"),
        // Burger King NZ shares com.emn8.mobilem8.nativeapp.bk with US — see US section (regionHint = "US,NZ")
        createMapping("KFC",           "com.kfcnz.orderserv",                   "KFC New Zealand",    "fast_food",   isEnabled = false, regionHint = "NZ"),
        createMapping("Domino's",      "au.com.dominos.olo.android.app.nz",     "Domino's",           "fast_food",   isEnabled = false, regionHint = "NZ"),
        createMapping("Pizza Hut",     "nz.co.pizzahut",                        "Pizza Hut",          "fast_food",   isEnabled = false, regionHint = "NZ"), // verify package
        createMapping("BurgerFuel",    "com.como.prod909420230817",             "BurgerFuel VIB Club","fast_food",   isEnabled = false, regionHint = "NZ")

    )

    private fun createMapping(
        businessName: String,
        packageName: String,
        appName: String,
        category: String,
        isEnabled: Boolean = true,
        regionHint: String? = null
    ): BusinessAppMapping = BusinessAppMapping(
        businessName = businessName,
        packageName = packageName,
        appName = appName,
        category = category,
        isEnabled = isEnabled,
        regionHint = regionHint,
        latitude = null,
        longitude = null,
        geofenceRadius = 200,
        version = 1
    )
}
