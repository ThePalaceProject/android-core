dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-networkconnectivity-api"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.slf4j)
}
