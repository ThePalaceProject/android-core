dependencies {
    implementation(project(":palace-links"))
    implementation(project(":palace-opds2"))
    implementation(project(":palace-opds2-parser-api"))
    implementation(project(":palace-parser-api"))

    implementation(libs.io7m.dixmont.core)
    implementation(libs.irradia.mime.api)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.kotlin)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.webpub.core)
    implementation(libs.slf4j)
}
