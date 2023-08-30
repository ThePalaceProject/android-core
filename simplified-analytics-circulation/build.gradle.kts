dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-analytics-api"))
    implementation(project(":simplified-threads"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.slf4j)
}
