dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-json"))
    implementation(project(":simplified-accounts-registry-api"))
    implementation(project(":simplified-accounts-source-spi"))
    implementation(project(":simplified-buildconfig-api"))
    implementation(project(":simplified-files"))
    implementation(project(":simplified-links"))
    implementation(project(":simplified-opds-auth-document-api"))
    implementation(project(":simplified-opds2-irradia"))
    implementation(project(":simplified-opds2-parser-api"))
    implementation(project(":simplified-parser-api"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-taskrecorder-api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.io7m.jfunctional)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.slf4j)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
