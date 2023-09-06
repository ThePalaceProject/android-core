dependencies {
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-books-registry-api"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-ui-thread-api"))

    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)
}
