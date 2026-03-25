package com.example.nearbyappswidget.data.local

/**
 * Initial hard‑coded dataset of UK business chains.
 * Updated with exact package names installed on the test device (2026‑03‑19).
 * Coordinates are dummy London‑based offsets – will be populated from geocoding APIs later.
 */
object InitialDataset {
    fun getMappings(): List<BusinessAppMapping> = listOf(
        createMapping(
            businessName = "Tesco",
            packageName = "com.tesco.grocery.view",
            appName = "Tesco Grocery",
            category = "supermarket",
            latitude = 51.5074,
            longitude = -0.1278
        ),
        createMapping(
            businessName = "McDonald's",
            packageName = "com.mcdonalds.app.uk",
            appName = "McDonald's",
            category = "fast_food",
            latitude = 51.5074 + 0.001,
            longitude = -0.1278 + 0.001
        ),
        createMapping(
            businessName = "Greggs",
            packageName = "com.mobile5.greggs",
            appName = "Greggs",
            category = "bakery",
            latitude = 51.5074 + 0.002,
            longitude = -0.1278 + 0.002
        ),
        createMapping(
            businessName = "Costa Coffee",
            packageName = "uk.co.club.costa.costa",
            appName = "Costa Coffee",
            category = "coffee",
            latitude = 51.5074 + 0.003,
            longitude = -0.1278 + 0.003
        ),
        createMapping(
            businessName = "Asda",
            packageName = "com.asda.rewards",
            appName = "Asda Rewards",
            category = "supermarket",
            latitude = 51.5074 + 0.004,
            longitude = -0.1278 + 0.004
        ),
        createMapping(
            businessName = "Lidl",
            packageName = "com.lidl.eci.lidlplus",
            appName = "Lidl Plus",
            category = "supermarket",
            latitude = 51.5074 + 0.005,
            longitude = -0.1278 + 0.005
        ),
        createMapping(
            businessName = "Premier Inn",
            packageName = "com.whitbread.premierinn",
            appName = "Premier Inn",
            category = "hotel",
            latitude = 51.5074 + 0.006,
            longitude = -0.1278 + 0.006
        ),
        createMapping(
            businessName = "Starbucks",
            packageName = "com.starbucks.mobilecard",
            appName = "Starbucks",
            category = "coffee",
            latitude = 51.5074 + 0.007,
            longitude = -0.1278 + 0.007
        ),
        createMapping(
            businessName = "Boots",
            packageName = "com.boots",
            appName = "Boots",
            category = "pharmacy",
            latitude = 51.5074 + 0.008,
            longitude = -0.1278 + 0.008
        ),
        createMapping(
            businessName = "WHSmith",
            packageName = "com.whsmith.android",
            appName = "WHSmith",
            category = "retail",
            latitude = 51.5074 + 0.009,
            longitude = -0.1278 + 0.009
        )
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