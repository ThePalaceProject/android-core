dependencies {
    implementation(project(":simplified-json-core"))
    implementation(project(":simplified-links"))
    implementation(project(":simplified-parser-api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)
}
