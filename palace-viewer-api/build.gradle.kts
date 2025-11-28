dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-database-api"))
    implementation(project(":palace-analytics-api"))
    implementation(project(":palace-books-api"))
    implementation(project(":palace-mdc"))
    implementation(project(":palace-opds-core"))
    implementation(project(":palace-profiles-api"))
    implementation(project(":palace-profiles-controller-api"))
    implementation(project(":palace-services-api"))
    implementation(project(":palace-viewer-spi"))

    implementation(libs.io7m.jfunctional)
    implementation(libs.irradia.mime.api)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.drm.core)
    implementation(libs.slf4j)
}
