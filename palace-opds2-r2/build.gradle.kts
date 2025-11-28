dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-opds2"))
    implementation(project(":palace-opds2-parser-api"))
    implementation(project(":palace-parser-api"))

    implementation(libs.joda.time)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.r2.opds)
    implementation(libs.r2.shared)
}
