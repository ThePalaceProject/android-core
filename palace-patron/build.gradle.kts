dependencies {
    implementation(project(":palace-json-core"))
    implementation(project(":palace-links"))
    implementation(project(":palace-links-json"))
    implementation(project(":palace-parser-api"))
    implementation(project(":palace-patron-api"))

    implementation(libs.jackson.databind)
    implementation(libs.jackson.core)
    implementation(libs.joda.time)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
}
