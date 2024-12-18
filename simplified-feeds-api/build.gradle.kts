dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-books-bundled-api"))
    implementation(project(":simplified-books-database-api"))
    implementation(project(":simplified-books-formats-api"))
    implementation(project(":simplified-books-registry-api"))
    implementation(project(":simplified-content-api"))
    implementation(project(":simplified-json-core"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-taskrecorder-api"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jnull)
    implementation(libs.irradia.mime.api)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.slf4j)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
