dependencies {
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-books-registry-api"))
    implementation(project(":simplified-profiles-api"))
    implementation(project(":simplified-threads"))

    implementation(libs.androidx.core)
    implementation(libs.google.guava)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)
}
