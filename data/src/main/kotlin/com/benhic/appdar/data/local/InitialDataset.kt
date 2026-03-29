package com.benhic.appdar.data.local

/**
 * Initial hard-coded dataset of business chains — UK + US.
 * No coordinates are stored here; real branch locations are resolved at runtime
 * from OpenStreetMap via NearbyBranchFinder and then persisted back to the
 * database, so the widget remains accurate even when offline.
 *
 * Businesses without a nearby OSM result are excluded from the widget rather
 * than showing a misleading placeholder location.
 *
 * All entries are disabled by default — they can be toggled on/off from the
 * Places screen.
 */
object InitialDataset {
    fun getMappings(): List<BusinessAppMapping> = listOf(

        // ── UK Supermarkets ───────────────────────────────────────────────────
        createMapping("Tesco",        "com.tesco.grocery.view",         "Tesco",             "supermarket"),
        createMapping("Sainsbury's",  "com.sainsburys.gol",             "Sainsbury's",       "supermarket"),
        createMapping("Asda",         "com.asda.android",               "Asda Groceries",    "supermarket"),
        createMapping("Morrisons",    "com.morrisons.atm.mobile.android","Morrisons Grocery", "supermarket"),
        createMapping("Aldi",         "de.apptiv.business.android.aldi_uk","Aldi Offers",    "supermarket"),
        createMapping("Lidl",         "com.lidl.eci.lidlplus",          "Lidl Plus",         "supermarket"),
        createMapping("Waitrose",     "com.waitrose.groceries",         "Waitrose",          "supermarket"),
        createMapping("M&S",          "com.marksandspencer.app",        "M&S",               "supermarket"),
        createMapping("Iceland",      "com.iceland.android",            "Iceland",           "supermarket"),
        createMapping("Co-op",        "uk.co.coop.app",                 "Co-op",             "supermarket"),

        // ── UK Coffee & Bakery ────────────────────────────────────────────────
        createMapping("Costa Coffee", "uk.co.club.costa.costa",         "Costa Coffee",      "coffee"),
        createMapping("Starbucks",    "com.starbucks.mobilecard",       "Starbucks",         "coffee"),
        createMapping("Caffè Nero",   "com.caffenero.mobile",           "Caffè Nero",        "coffee"),
        createMapping("Pret A Manger","com.pret.amangerapp",            "Pret A Manger",     "coffee"),
        createMapping("Greggs",       "com.mobile5.greggs",             "Greggs",            "bakery"),

        // ── UK Fast Food ──────────────────────────────────────────────────────
        createMapping("McDonald's",   "com.mcdonalds.app.uk",           "McDonald's",        "fast_food"),
        createMapping("Burger King",  "com.emn8.mobilem8.nativeapp.bkuk","Burger King",      "fast_food"),
        createMapping("KFC",          "com.yum.colonelsclub",           "KFC",               "fast_food"),
        createMapping("Subway",       "com.subway.mobile.subwayapp03",  "Subway",            "fast_food"),
        createMapping("Nando's",      "nandos.android.app",             "Nando's",           "fast_food"),
        createMapping("Five Guys",    "com.fiveguys.fiveguysuk",        "Five Guys",         "fast_food"),
        createMapping("Wagamama",     "com.wagamama.soulclubapp",       "wagamama",          "fast_food"),
        createMapping("Domino's",     "uk.co.dominos.android",          "Domino's",          "fast_food"),
        createMapping("Papa John's",  "com.papajohns.android",          "Papa John's",       "fast_food"),
        createMapping("Pizza Hut",    "com.pizzahutuk.orderingApp",     "Pizza Hut",         "fast_food"),

        // ── UK Casual Dining / Pubs ───────────────────────────────────────────
        createMapping("Wetherspoons", "com.wetherspoon.orderandpay",    "Wetherspoons",      "pub"),

        // ── UK Pharmacy & Retail ──────────────────────────────────────────────
        createMapping("Boots",        "com.boots.flagship.android",     "Boots",             "pharmacy"),
        createMapping("WHSmith",      "com.whsmith.mywhsmith.android",  "WHSmith",           "retail"),
        createMapping("IKEA",         "com.ingka.ikea.app",             "IKEA",              "retail"),

        // ── UK Hotels ─────────────────────────────────────────────────────────
        createMapping("Premier Inn",  "com.whitbread.premierinn",       "Premier Inn",       "hotel"),
        createMapping("Travelodge",   "com.travelodge.rooms",           "Travelodge",        "hotel"),

        // ── Global Hotels (also useful in US) ────────────────────────────────
        createMapping("Hilton",       "com.hilton.android.hhonors",     "Hilton Honors",     "hotel"),
        createMapping("Marriott",     "com.marriott.mrt",               "Marriott Bonvoy",   "hotel"),
        createMapping("Holiday Inn",  "com.ihg.apps.android",           "IHG Hotels",        "hotel"),

        // ── US Supermarkets & Retail ──────────────────────────────────────────
        createMapping("Walmart",      "com.walmart.android",            "Walmart",           "supermarket"),
        createMapping("Target",       "com.target.ui",                  "Target",            "retail"),
        createMapping("Costco",       "com.costco.mobileapp",           "Costco",            "supermarket"),
        createMapping("Whole Foods",  "com.amazon.wholefoods",          "Whole Foods",       "supermarket"),
        createMapping("Walgreens",    "com.walgreens",                  "Walgreens",         "pharmacy"),
        createMapping("CVS",          "com.cvs.launchers.cvs",          "CVS Pharmacy",      "pharmacy"),

        // ── US Fast Food & Coffee ─────────────────────────────────────────────
        createMapping("McDonald's (US)",  "com.mcdonalds.app",              "McDonald's",    "fast_food"),
        createMapping("Burger King (US)", "com.emn8.mobilem8.nativeapp.bk", "Burger King",   "fast_food"),
        createMapping("KFC (US)",         "com.yum.kfc",                    "KFC",           "fast_food"),
        createMapping("Taco Bell",        "com.tacobell.android.activity",  "Taco Bell",     "fast_food"),
        createMapping("Chipotle",         "com.chipotle.mobile",            "Chipotle",      "fast_food"),
        createMapping("Chick-fil-A",      "com.chickfila.cfaone",           "Chick-fil-A",   "fast_food"),
        createMapping("Dunkin'",          "com.dunkindonuts.mobile",        "Dunkin'",       "coffee"),
        createMapping("Panera Bread",     "com.panerabread.app",            "Panera Bread",  "fast_food"),
        createMapping("Shake Shack",      "com.shackshack.app",             "Shake Shack",   "fast_food"),
        createMapping("Domino's (US)",    "com.dominospizza",               "Domino's",      "fast_food")

    )

    private fun createMapping(
        businessName: String,
        packageName: String,
        appName: String,
        category: String
    ): BusinessAppMapping = BusinessAppMapping(
        businessName = businessName,
        packageName = packageName,
        appName = appName,
        category = category,
        latitude = null,
        longitude = null,
        geofenceRadius = 200,
        version = 1
    )
}
