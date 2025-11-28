dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-books-api"))
    implementation(project(":palace-books-formats-api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)
}
