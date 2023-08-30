dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-database-api"))
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-books-borrowing"))
    implementation(project(":simplified-feeds-api"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-taskrecorder-api"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.irradia.mime.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
