plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.hilt.android.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("android.application") {
            id = "android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("android.library") {
            id = "android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("kotlin.android") {
            id = "kotlin.android"
            implementationClass = "KotlinAndroidConventionPlugin"
        }
        register("dagger.hilt") {
            id = "dagger.hilt"
            implementationClass = "DaggerHiltConventionPlugin"
        }
    }
}

// Note: We'll need to define libs in the build-logic's own version catalog.
// For simplicity, we can rely on the root project's version catalog.
// This is a placeholder; the architect will refine.