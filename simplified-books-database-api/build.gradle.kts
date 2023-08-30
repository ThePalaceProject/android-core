dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-books-formats-api"))
    implementation(project(":simplified-opds-core"))

    implementation(libs.io7m.jnull)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.jcip.annotations)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.audiobook.api)
    implementation(libs.palace.drm.core)
}
