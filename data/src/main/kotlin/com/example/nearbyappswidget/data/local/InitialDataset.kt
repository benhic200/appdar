package com.example.nearbyappswidget.data.local

/**
 * Initial hard-coded dataset of business chains — UK + US.
 * Coordinates are approximate city-centre fallbacks only; real branch locations
 * are resolved at runtime from OpenStreetMap via NearbyBranchFinder.
 *
 * UK entries use London coords; US entries use New York coords as seed fallback.
 * All entries are disabled by default for users outside the relevant market — they can
 * toggle them on/off from the Places screen.
 */
object InitialDataset {
    fun getMappings(): List<BusinessAppMapping> = listOf(

        // ── UK Supermarkets ───────────────────────────────────────────────────
        createMapping("Tesco",        "com.tesco.grocery.view",         "Tesco",             "supermarket", 51.5074, -0.1278),
        createMapping("Sainsbury's",  "com.sainsburys.smkt.android.shop","Sainsbury's",       "supermarket", 51.5074, -0.1279),
        createMapping("Asda",         "com.asda.rewards",               "Asda Rewards",      "supermarket", 51.5074, -0.1280),
        createMapping("Morrisons",    "com.morrison.morrisons",         "Morrisons",         "supermarket", 51.5074, -0.1281),
        createMapping("Aldi",         "com.aldi.offers",                "Aldi Offers",       "supermarket", 51.5074, -0.1282),
        createMapping("Lidl",         "com.lidl.eci.lidlplus",          "Lidl Plus",         "supermarket", 51.5074, -0.1283),
        createMapping("Waitrose",     "com.waitrose.picnic",            "Waitrose",          "supermarket", 51.5074, -0.1284),
        createMapping("M&S",          "com.marks.spencer.android",      "M&S",               "supermarket", 51.5074, -0.1285),
        createMapping("Iceland",      "com.iceland.android",            "Iceland",           "supermarket", 51.5074, -0.1286),
        createMapping("Co-op",        "coop.co.uk.membership",          "Co-op Membership",  "supermarket", 51.5074, -0.1287),

        // ── UK Coffee & Bakery ────────────────────────────────────────────────
        createMapping("Costa Coffee", "uk.co.club.costa.costa",         "Costa Coffee",      "coffee",   51.5075, -0.1278),
        createMapping("Starbucks",    "com.starbucks.mobilecard",       "Starbucks",         "coffee",   51.5075, -0.1279),
        createMapping("Caffè Nero",   "com.caffenero.mobile",           "Caffè Nero",        "coffee",   51.5075, -0.1280),
        createMapping("Pret A Manger","com.pret.amangerapp",            "Pret A Manger",     "coffee",   51.5075, -0.1281),
        createMapping("Greggs",       "com.mobile5.greggs",             "Greggs",            "bakery",   51.5075, -0.1282),

        // ── UK Fast Food ──────────────────────────────────────────────────────
        createMapping("McDonald's",   "com.mcdonalds.app.uk",           "McDonald's",        "fast_food", 51.5076, -0.1278),
        createMapping("Burger King",  "com.emn8.mobilem8.nativeapp.bkuk","Burger King",      "fast_food", 51.5076, -0.1279),
        createMapping("KFC",          "uk.co.kfc.kfc_app",              "KFC",               "fast_food", 51.5076, -0.1280),
        createMapping("Subway",       "com.subway.mobile.subwayapp03",  "Subway",            "fast_food", 51.5076, -0.1281),
        createMapping("Nando's",      "com.nandos.android.activity",    "Nando's",           "fast_food", 51.5076, -0.1282),
        createMapping("Five Guys",    "com.fiveguys.fiveguys",          "Five Guys",         "fast_food", 51.5076, -0.1283),
        createMapping("Wagamama",     "uk.co.wagamama",                 "wagamama",          "fast_food", 51.5076, -0.1284),
        createMapping("Domino's",     "uk.co.dominos.android",          "Domino's",          "fast_food", 51.5076, -0.1285),
        createMapping("Papa John's",  "com.papajohns.android",          "Papa John's",       "fast_food", 51.5076, -0.1286),
        createMapping("Pizza Hut",    "com.pizzahutuk",                 "Pizza Hut",         "fast_food", 51.5076, -0.1287),
        createMapping("Leon",         "com.leon.leon",                  "Leon",              "fast_food", 51.5076, -0.1288),

        // ── UK Casual Dining / Pubs ───────────────────────────────────────────
        createMapping("Wetherspoons", "com.jdwetherspoon.apps.android", "Wetherspoons",      "pub",      51.5077, -0.1278),

        // ── UK Pharmacy & Retail ──────────────────────────────────────────────
        createMapping("Boots",        "com.boots",                      "Boots",             "pharmacy", 51.5077, -0.1279),
        createMapping("WHSmith",      "com.whsmith.android",            "WHSmith",           "retail",   51.5077, -0.1280),

        // ── UK Hotels ─────────────────────────────────────────────────────────
        createMapping("Premier Inn",  "com.whitbread.premierinn",       "Premier Inn",       "hotel",    51.5077, -0.1281),
        createMapping("Travelodge",   "com.travelodge.rooms",           "Travelodge",        "hotel",    51.5077, -0.1282),

        // ── Global Hotels (also useful in US) ────────────────────────────────
        createMapping("Hilton",       "com.hilton.android.hhonors",     "Hilton Honors",     "hotel",    51.5077, -0.1283),
        createMapping("Marriott",     "com.marriott.mrt",               "Marriott Bonvoy",   "hotel",    51.5077, -0.1284),
        createMapping("Holiday Inn",  "com.ihg.apps.android",           "IHG Hotels",        "hotel",    51.5077, -0.1285),

        // ── US Supermarkets & Retail ──────────────────────────────────────────
        createMapping("Walmart",      "com.walmart.android",            "Walmart",           "supermarket", 40.7128, -74.0060),
        createMapping("Target",       "com.target.ui",                  "Target",            "retail",      40.7128, -74.0061),
        createMapping("Costco",       "com.costco.mobile.android",      "Costco",            "supermarket", 40.7128, -74.0062),
        createMapping("Whole Foods",  "com.amazon.wholefoods",          "Whole Foods",       "supermarket", 40.7128, -74.0063),
        createMapping("Walgreens",    "com.walgreens",                  "Walgreens",         "pharmacy",    40.7128, -74.0064),
        createMapping("CVS",          "com.cvs.launchers.cvs",          "CVS Pharmacy",      "pharmacy",    40.7128, -74.0065),

        // ── US Fast Food & Coffee ─────────────────────────────────────────────
        createMapping("McDonald's (US)",  "com.mcdonalds.app",                "McDonald's",        "fast_food", 40.7129, -74.0060),
        createMapping("Burger King (US)", "com.emn8.mobilem8.nativeapp.bk",   "Burger King",       "fast_food", 40.7129, -74.0061),
        createMapping("KFC (US)",         "com.yum.kfc",                      "KFC",               "fast_food", 40.7129, -74.0062),
        createMapping("Taco Bell",        "com.tacobell.android.activity",    "Taco Bell",         "fast_food", 40.7129, -74.0063),
        createMapping("Chipotle",         "com.chipotle.mobile",              "Chipotle",          "fast_food", 40.7129, -74.0064),
        createMapping("Chick-fil-A",      "com.chickfila.cfaone",             "Chick-fil-A",       "fast_food", 40.7129, -74.0065),
        createMapping("Dunkin'",          "com.dunkindonuts.mobile",          "Dunkin'",           "coffee",    40.7129, -74.0066),
        createMapping("Panera Bread",     "com.panerabread.app",              "Panera Bread",      "fast_food", 40.7129, -74.0067),
        createMapping("Shake Shack",      "com.shackburger.app",              "Shake Shack",       "fast_food", 40.7129, -74.0068),
        createMapping("Domino's (US)",    "com.dominospizza",                 "Domino's",          "fast_food", 40.7129, -74.0069)

    ).mapNotNull { it.withBoundingBox() }

    private fun createMapping(
        businessName: String,
        packageName: String,
        appName: String,
        category: String,
        latitude: Double,
        longitude: Double
    ): BusinessAppMapping = BusinessAppMapping(
        businessName = businessName,
        packageName = packageName,
        appName = appName,
        category = category,
        latitude = latitude,
        longitude = longitude,
        geofenceRadius = 200,
        version = 1
    )
}
