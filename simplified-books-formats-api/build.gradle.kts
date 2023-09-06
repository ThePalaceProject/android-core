dependencies {
    implementation(project(":simplified-books-api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.slf4j)
}
