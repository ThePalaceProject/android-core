dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-announcements"))
    implementation(project(":palace-books-formats-api"))
    implementation(project(":palace-files"))
    implementation(project(":palace-json-core"))
    implementation(project(":palace-links"))
    implementation(project(":palace-links-json"))
    implementation(project(":palace-parser-api"))
    implementation(project(":palace-patron-api"))
    implementation(project(":palace-taskrecorder-api"))

    implementation(libs.io7m.jfunctional)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.drm.core)
    implementation(libs.palace.webpub.core)
    implementation(libs.slf4j)
}
