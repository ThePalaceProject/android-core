dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-opds-core"))

    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.slf4j)
}
