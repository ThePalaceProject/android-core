dependencies {
    implementation(project(":simplified-opds2"))
    implementation(project(":simplified-opds2-parser-api"))
    implementation(project(":simplified-parser-api"))

    implementation(libs.joda.time)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.r2.opds)
    implementation(libs.r2.shared)
}
