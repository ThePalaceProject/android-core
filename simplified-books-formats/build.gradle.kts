dependencies {
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-books-formats-api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)
}
