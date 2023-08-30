dependencies {
    implementation(project(":simplified-json-core"))
    implementation(project(":simplified-links"))
    implementation(project(":simplified-links-json"))
    implementation(project(":simplified-parser-api"))
    implementation(project(":simplified-patron-api"))

    implementation(libs.jackson.databind)
    implementation(libs.jackson.core)
    implementation(libs.joda.time)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
}
