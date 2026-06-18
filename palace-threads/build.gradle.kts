plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.google.guava)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
}
