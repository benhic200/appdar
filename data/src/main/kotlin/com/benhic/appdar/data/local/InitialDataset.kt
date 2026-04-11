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
        createMapping("Sainsbury's",  "com.ga.loyalty.android.nectar.activities", "Sainsbury's Nectar", "supermarket"),
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
        createMapping("Wetherspoons",  "com.wetherspoon.orderandpay",    "Wetherspoons",      "pub"),
        createMapping("Pizza Express", "com.pizzaexpress.pizzaexpressapp","Pizza Express",    "casual_dining"),
        createMapping("Zizzi",         "com.zizzi.app",                  "Zizzi",             "casual_dining"),
        createMapping("Yo! Sushi",     "com.yo.sushi.vip",               "YO! Sushi",         "casual_dining"),
        createMapping("TGI Fridays",   "com.tgifridays.loyalty.uk",      "TGI Fridays",       "casual_dining"),

        // ── UK Pharmacy & Retail ──────────────────────────────────────────────
        createMapping("Boots",         "com.boots.flagship.android",     "Boots",             "pharmacy"),
        createMapping("Superdrug",     "com.superdrug.beautycardapp",    "Superdrug",         "pharmacy"),
        createMapping("WHSmith",       "com.whsmith.mywhsmith.android",  "WHSmith",           "retail"),
        createMapping("Argos",         "com.argos.android",              "Argos",             "retail"),
        createMapping("Next",          "com.next.android",               "Next",              "retail"),
        createMapping("JD Sports",     "com.jdsports.android",           "JD Sports",         "retail"),
        createMapping("Sports Direct", "com.sportsdirect.android",       "Sports Direct",     "retail"),
        createMapping("Currys",        "uk.co.currys.android",           "Currys",            "retail"),
        createMapping("IKEA",          "com.ingka.ikea.app",             "IKEA",              "retail"),

        // ── UK Cinema ─────────────────────────────────────────────────────────
        createMapping("Odeon",         "com.odeon.android",              "Odeon",             "entertainment"),
        createMapping("Vue",           "com.vue.vueapp",                 "Vue Cinema",        "entertainment"),
        createMapping("Cineworld",     "com.cineworld.android",          "Cineworld",         "entertainment"),

        // ── UK Hotels ─────────────────────────────────────────────────────────
        createMapping("Premier Inn",   "com.whitbread.premierinn",       "Premier Inn",       "hotel"),
        createMapping("Travelodge",    "com.travelodge.rooms",           "Travelodge",        "hotel"),

        // ── Global Hotels (also useful in US) ────────────────────────────────
        createMapping("Hilton",        "com.hilton.android.hhonors",     "Hilton Honors",     "hotel"),
        createMapping("Marriott",      "com.marriott.mrt",               "Marriott Bonvoy",   "hotel"),
        createMapping("Holiday Inn",   "com.ihg.apps.android",           "IHG Hotels",        "hotel"),

        // ── Global Fuel ───────────────────────────────────────────────────────
        createMapping("BP",            "com.bp.android.bpme",           "BPme",              "fuel"),
        createMapping("Shell",         "com.shell.android",              "Shell Go+",         "fuel"),

        // ── US-only Supermarkets, Pharmacy & Retail (disabled by default) ───────
        createMapping("Walmart",       "com.walmart.android",            "Walmart",           "supermarket", isEnabled = false),
        createMapping("Target",        "com.target.ui",                  "Target",            "retail",      isEnabled = false),
        createMapping("Whole Foods",   "com.amazon.wholefoods",          "Whole Foods",       "supermarket", isEnabled = false),
        createMapping("Walgreens",     "com.walgreens",                  "Walgreens",         "pharmacy",    isEnabled = false),
        createMapping("CVS",           "com.cvs.launchers.cvs",          "CVS Pharmacy",      "pharmacy",    isEnabled = false),
        createMapping("Panera Bread",  "com.panerabread.app",            "Panera Bread",      "fast_food",   isEnabled = false),

        // ── US app variants for global fast-food chains (disabled by default) ─
        createMapping("McDonald's (US)",  "com.mcdonalds.app",              "McDonald's",    "fast_food",  isEnabled = false),
        createMapping("Burger King (US)", "com.emn8.mobilem8.nativeapp.bk", "Burger King",   "fast_food",  isEnabled = false),
        createMapping("KFC (US)",         "com.yum.kfc",                    "KFC",           "fast_food",  isEnabled = false),
        createMapping("Domino's (US)",    "com.dominospizza",               "Domino's",      "fast_food",  isEnabled = false),

        // ── US-origin brands with UK presence (enabled by default) ────────────
        createMapping("Costco",       "com.costco.mobileapp",           "Costco",            "supermarket"),
        createMapping("Taco Bell",    "com.tacobell.android.activity",  "Taco Bell",         "fast_food"),
        createMapping("Chipotle",     "com.chipotle.mobile",            "Chipotle",          "fast_food"),
        createMapping("Chick-fil-A",  "com.chickfila.cfaone",           "Chick-fil-A",       "fast_food"),
        createMapping("Dunkin'",      "com.dunkindonuts.mobile",        "Dunkin'",           "coffee"),
        createMapping("Shake Shack",  "com.shackshack.app",             "Shake Shack",       "fast_food"),

        // ── Australia (package names should be verified against AU Play Store) ─
        createMapping("Woolworths",        "com.woolworths",                          "Woolworths",        "supermarket", isEnabled = false),
        createMapping("Coles",             "au.com.coles",                            "Coles",             "supermarket", isEnabled = false),
        createMapping("Hungry Jack's",     "com.hungryjacks.ordering",               "Hungry Jack's",     "fast_food",   isEnabled = false),
        createMapping("Chemist Warehouse", "com.chemistwarehouse.chemistwarehouseandroid", "Chemist Warehouse", "pharmacy", isEnabled = false),
        createMapping("Dan Murphy's",      "au.com.danmurphys",                      "Dan Murphy's",      "retail",      isEnabled = false),
        createMapping("JB Hi-Fi",          "com.jbhifi.app",                         "JB Hi-Fi",          "retail",      isEnabled = false),
        createMapping("Bunnings",          "com.bunnings.au",                        "Bunnings",          "retail",      isEnabled = false),
        createMapping("Officeworks",       "com.officeworks.android",                "Officeworks",       "retail",      isEnabled = false),
        createMapping("Myer",              "com.myeronline.myer",                    "Myer",              "retail",      isEnabled = false),
        createMapping("Event Cinemas",     "au.com.eventcinemas",                    "Event Cinemas",     "entertainment", isEnabled = false),
        createMapping("Hoyts",             "au.com.hoyts",                           "Hoyts",             "entertainment", isEnabled = false),

        // ── New Zealand (package names should be verified against NZ Play Store) ─
        createMapping("Countdown",     "nz.co.countdown.android",          "Countdown",     "supermarket", isEnabled = false),
        createMapping("New World",     "co.nz.foodstuffs.newworld",        "New World",     "supermarket", isEnabled = false),
        createMapping("The Warehouse", "nz.co.thewarehouse",               "The Warehouse", "retail",      isEnabled = false),
        createMapping("Z",             "nz.co.zpetrol",                    "Z Energy",      "fuel",        isEnabled = false),
        createMapping("Mitre 10",      "nz.co.mitre10",                    "Mitre 10",      "retail",      isEnabled = false)

    )

    private fun createMapping(
        businessName: String,
        packageName: String,
        appName: String,
        category: String,
        isEnabled: Boolean = true
    ): BusinessAppMapping = BusinessAppMapping(
        businessName = businessName,
        packageName = packageName,
        appName = appName,
        category = category,
        isEnabled = isEnabled,
        latitude = null,
        longitude = null,
        geofenceRadius = 200,
        version = 1
    )
}
