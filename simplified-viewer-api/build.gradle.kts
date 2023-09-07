dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-database-api"))
    implementation(project(":simplified-analytics-api"))
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-profiles-api"))
    implementation(project(":simplified-profiles-controller-api"))
    implementation(project(":simplified-services-api"))
    implementation(project(":simplified-viewer-spi"))

    implementation(libs.io7m.jfunctional)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.drm.core)
    implementation(libs.slf4j)
}
