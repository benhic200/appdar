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
        createMapping("Chipotle",    "com.chipotle.mena",             "Chipotle",        "fast_food"),
        createMapping("Shake Shack", "com.thesweetshakeshack",        "Shake Shack",     "fast_food"),

        // ── United States ─────────────────────────────────────────────────────

        // ── US Supermarkets ───────────────────────────────────────────────────
        createMapping("Walmart",      "com.walmart.android",                    "Walmart",         "supermarket", regionHint = "US"),
        createMapping("Target",       "com.target.ui",                          "Target",          "retail"     , regionHint = "US"),
        createMapping("Kroger",       "com.kroger.mobile",                      "Kroger",          "supermarket", regionHint = "US"),
        createMapping("Safeway",      "com.safeway.client.android.safeway",     "Safeway",         "supermarket", regionHint = "US"),
        createMapping("Albertsons",   "com.safeway.client.android.albertsons",  "Albertsons",      "supermarket", regionHint = "US"),
        createMapping("Publix",       "com.publix.main",                        "Publix",          "supermarket", regionHint = "US"),

        // ── US Coffee & Bakery ────────────────────────────────────────────────
        createMapping("Dunkin'", "com.dunkind.app", "Dunkin'", "coffee", regionHint = "US"),

        // ── US Pharmacy ───────────────────────────────────────────────────────
        createMapping("CVS", "com.cvs.launchers.cvs", "CVS Pharmacy", "pharmacy", regionHint = "US"),
        createMapping("Walgreens", "com.usablenet.mobile.walgreen", "Walgreens", "pharmacy", regionHint = "US"),

        // ── US Fast Food ──────────────────────────────────────────────────────
        createMapping("McDonald's", "com.mcdonalds.app", "McDonald's", "fast_food", regionHint = "US"),
        createMapping("Burger King", "com.emn8.mobilem8.nativeapp.bk", "Burger King", "fast_food", regionHint = "US,NZ")  // same app used in NZ,
        createMapping("KFC", "com.kfc.us.mobile", "KFC", "fast_food", regionHint = "US"),
        createMapping("Chick-fil-A", "com.chickfila.cfaflagship", "Chick-fil-A", "fast_food", regionHint = "US"),
        createMapping("Wendy's", "com.wendys.nutritiontool", "Wendy's", "fast_food", regionHint = "US"),
        createMapping("Panera Bread", "com.panera.bread", "Panera Bread", "fast_food", regionHint = "US"),
        createMapping("Domino's", "com.dominospizza", "Domino's", "fast_food", regionHint = "US"),
        createMapping("Pizza Hut", "com.yum.pizzahut", "Pizza Hut", "fast_food", regionHint = "US"),

        // ── US Cinema ─────────────────────────────────────────────────────────
        createMapping("AMC Theatres",  "com.amc",                      "AMC Theatres",     "entertainment", regionHint = "US"),
        createMapping("Regal",         "com.fandango.regal",           "Regal",            "entertainment", regionHint = "US"),

        // ── US Fast Food (new) ────────────────────────────────────────────────
        createMapping("Popeyes",        "com.rbi.popeyes",             "Popeyes",          "fast_food", regionHint = "US"),
        createMapping("Sonic",          "com.sonicdrivein.sonic",      "SONIC Drive-In",   "fast_food", regionHint = "US"), // verify package
        createMapping("Dairy Queen",    "com.dairyqueen.android",      "Dairy Queen",      "fast_food", regionHint = "US"), // verify package
        createMapping("Jack in the Box","com.jackinthebox.android",    "Jack in the Box",  "fast_food", regionHint = "US"), // verify package
        createMapping("Panda Express",  "com.pandarg.pandaexpress",    "Panda Express",    "fast_food", regionHint = "US"), // verify package
        createMapping("Arby's",         "com.arbys.mobile",            "Arby's",           "fast_food", regionHint = "US"), // verify package
        createMapping("Wingstop",       "com.wingstop.android",        "Wingstop",         "fast_food", regionHint = "US"), // verify package

        // ── US Coffee (new) ───────────────────────────────────────────────────
        createMapping("Dutch Bros", "com.dutchbros.android", "Dutch Bros", "coffee", regionHint = "US")  // verify package,

        // ── US Retail (new) ───────────────────────────────────────────────────
        createMapping("Trader Joe's",   "com.traderjoes.android",      "Trader Joe's",     "supermarket", regionHint = "US"), // verify package
        createMapping("Best Buy", "com.bestbuy.android", "Best Buy", "retail", regionHint = "US"),
        createMapping("Home Depot", "com.thehomedepot", "The Home Depot", "retail", regionHint = "US"),
        createMapping("Lowe's", "com.lowes.android", "Lowe's", "retail", regionHint = "US"),
        createMapping("Dollar General", "com.dollargeneral.android", "Dollar General", "retail", regionHint = "US")  // verify package,
        createMapping("Dollar Tree", "com.dollartree.android", "Dollar Tree", "retail", regionHint = "US")  // verify package,
        createMapping("GameStop", "com.gamestop.powerup", "GameStop", "retail", regionHint = "US"),
        createMapping("Ulta Beauty", "com.ulta.android", "Ulta Beauty", "retail", regionHint = "US")  // verify package,

        // ── US Pharmacy (new) ─────────────────────────────────────────────────
        createMapping("Rite Aid", "com.riteaid.myriteaid", "Rite Aid", "pharmacy", regionHint = "US")  // verify package,

        // ── US Fitness (new) ──────────────────────────────────────────────────
        createMapping("Planet Fitness", "com.planetfitness.android", "Planet Fitness", "fitness", regionHint = "US")  // verify package,

        // ── Australia ─────────────────────────────────────────────────────────

        // ── AU Supermarkets ───────────────────────────────────────────────────
        createMapping("Woolworths",        "com.woolworths",                         "Woolworths",         "supermarket", regionHint = "AU"),
        createMapping("Coles",             "com.coles.android.shopmate",             "Coles",              "supermarket", regionHint = "AU"),
        createMapping("Aldi",              "de.apptiv.business.android.aldi_au",     "Aldi",               "supermarket", regionHint = "AU"),

        // ── AU Pharmacy & Retail ──────────────────────────────────────────────
        createMapping("Chemist Warehouse", "au.com.cwretailservices.cwapp",          "Chemist Warehouse",  "pharmacy",    regionHint = "AU"),
        createMapping("Dan Murphy's",      "au.com.danmurphys",                      "Dan Murphy's",       "retail",      regionHint = "AU"),
        createMapping("Bunnings", "com.bunnings.retail", "Bunnings", "retail", regionHint = "NZ"),
        createMapping("Myer",              "myer.com.android",                       "Myer",               "retail",      regionHint = "AU"),

        // ── AU Fast Food ──────────────────────────────────────────────────────
        createMapping("McDonald's",        "com.mcdonalds.au.gma",                   "MyMacca's",          "fast_food",   regionHint = "AU"),
        createMapping("Hungry Jack's",     "com.webling.hungryjacks",                "Hungry Jack's",      "fast_food",   regionHint = "AU"),
        createMapping("KFC",               "com.kfcaus.ordering",                    "KFC",                "fast_food",   regionHint = "AU"),
        createMapping("Domino's",          "au.com.dominos.olo.android.app",         "Domino's",           "fast_food",   regionHint = "AU"),
        createMapping("Pizza Hut",         "com.pizzahutau",                         "Pizza Hut",          "fast_food",   regionHint = "AU"),

        // ── AU Cinema ─────────────────────────────────────────────────────────
        createMapping("Event Cinemas",     "com.ahl.eventcinemas",                   "Event Cinemas",      "entertainment", regionHint = "AU"),
        createMapping("Hoyts",             "nz.co.vista.android.movie",              "HOYTS",              "entertainment", regionHint = "AU"),

        // ── AU Retail ─────────────────────────────────────────────────────────
        createMapping("Harvey Norman", "air.au.whitech.mobile.imagine.harveynorman", "Harvey Norman", "retail", regionHint = "AU"),
        createMapping("Kmart",             "au.com.kmart.android",                   "Kmart",              "retail",      regionHint = "AU"),
        createMapping("Big W",             "au.com.bigw.android",                    "Big W",              "retail",      regionHint = "AU"),

        // ── AU Fuel & Convenience ──────────────────────────────────────────────
        createMapping("7-Eleven",          "au.com.seveneleven.app",                 "7-Eleven",           "fuel",        regionHint = "AU"),

        // ── AU Pharmacy ───────────────────────────────────────────────────────
        createMapping("Priceline",         "au.com.priceline.android",               "Priceline",          "pharmacy",    regionHint = "AU"),

        // ── AU Fast Food (cont.) ──────────────────────────────────────────────
        createMapping("Red Rooster",       "au.com.redrooster.android",              "Red Rooster",        "fast_food",   regionHint = "AU"),

        // ── AU Coffee ─────────────────────────────────────────────────────────
        createMapping("The Coffee Club",   "com.thecoffeeclub.app",                  "The Coffee Club",    "coffee",      regionHint = "AU"),

        // ── New Zealand ───────────────────────────────────────────────────────

        // ── NZ Supermarkets ───────────────────────────────────────────────────
        createMapping("Woolworths NZ", "nz.co.countdown.android.pickup",        "Woolworths NZ",      "supermarket", regionHint = "NZ"),
        createMapping("New World",     "nz.co.newworld.clubcard",                "New World",          "supermarket", regionHint = "NZ"),
        createMapping("Pak'nSave",     "nz.co.progressive.paknsave",             "Pak'nSave",          "supermarket", regionHint = "NZ"),

        // ── NZ Retail ────────────────────────────────────────────────────────
        createMapping("The Warehouse", "nz.co.thewarehouse.wow",                 "The Warehouse",      "retail",      regionHint = "NZ"),
        createMapping("Farmers",       "nz.co.farmers.android",                  "Farmers",            "retail",      regionHint = "NZ"),
        createMapping("Noel Leeming",  "nz.co.noelleeming.android",              "Noel Leeming",       "retail",      regionHint = "NZ"),
        createMapping("Briscoes",      "nz.co.briscoes.android",                 "Briscoes",           "retail",      regionHint = "NZ"),
        createMapping("Harvey Norman", "com.harveynorman.app",                   "Harvey Norman",      "retail",      regionHint = "NZ"),
        createMapping("Kmart",         "au.com.kmart.android",                   "Kmart",              "retail",      regionHint = "NZ"),
        createMapping("Mitre 10",      "nz.co.mitre10.android",                  "Mitre 10",           "retail",      regionHint = "NZ"),
        createMapping("Bunnings",      "com.bunnings.retail",                    "Bunnings",           "retail",      regionHint = "NZ"),

        // ── NZ Fuel ───────────────────────────────────────────────────────────
        createMapping("Z",             "com.zenergy.zenergyapp.android.prod",    "Z App",              "fuel",        regionHint = "NZ"),

        // ── NZ Fast Food ──────────────────────────────────────────────────────
        createMapping("McDonald's",    "com.mcdonalds.mobileapp",                "McDonald's",         "fast_food",   regionHint = "NZ"),
        // Burger King NZ shares com.emn8.mobilem8.nativeapp.bk with US — see US section (regionHint = "US,NZ")
        createMapping("KFC",           "com.kfcnz.orderserv",                   "KFC New Zealand",    "fast_food",   regionHint = "NZ"),
        createMapping("Domino's",      "au.com.dominos.olo.android.app.nz",     "Domino's",           "fast_food",   regionHint = "NZ"),
        createMapping("Pizza Hut",     "nz.co.pizzahut",                        "Pizza Hut",          "fast_food",   regionHint = "NZ"),
        createMapping("BurgerFuel",    "com.como.prod909420230817",              "BurgerFuel VIB Club","fast_food",   regionHint = "NZ"),
        createMapping("Hell Pizza",    "com.hellpizza.app",                      "Hell Pizza",         "fast_food",   regionHint = "NZ")

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
