dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-announcements"))
    implementation(project(":simplified-books-formats-api"))
    implementation(project(":simplified-files"))
    implementation(project(":simplified-json-core"))
    implementation(project(":simplified-links"))
    implementation(project(":simplified-links-json"))
    implementation(project(":simplified-opds2"))
    implementation(project(":simplified-opds2-parser-api"))
    implementation(project(":simplified-parser-api"))
    implementation(project(":simplified-taskrecorder-api"))

    implementation(libs.io7m.jfunctional)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.drm.core)
    implementation(libs.slf4j)
}
