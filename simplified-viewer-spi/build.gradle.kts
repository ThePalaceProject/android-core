dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-books-api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
}
