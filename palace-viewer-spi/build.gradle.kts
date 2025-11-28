dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-books-api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
}
