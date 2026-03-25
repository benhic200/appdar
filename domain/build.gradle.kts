plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(21)
}