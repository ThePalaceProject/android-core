plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.audiobook.manifest.api)
}
