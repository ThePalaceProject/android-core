dependencies {
    api(project(":palace-parser-api"))
    api(project(":palace-links"))

    implementation(libs.joda.time)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
}
