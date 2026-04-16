pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Appdar"

include(":app")
include(":core")
include(":data")
include(":domain")
include(":feature-geofencing")
include(":feature-location")
include(":feature-widget")
include(":feature-widget-list")
include(":feature-settings")
include(":feature-geocoding")
include(":build-logic")