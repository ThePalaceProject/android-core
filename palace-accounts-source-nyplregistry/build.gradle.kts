dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-json"))
    implementation(project(":palace-accounts-registry-api"))
    implementation(project(":palace-accounts-source-spi"))
    implementation(project(":palace-announcements"))
    implementation(project(":palace-buildconfig-api"))
    implementation(project(":palace-files"))
    implementation(project(":palace-links"))
    implementation(project(":palace-opds-auth-document-api"))
    implementation(project(":palace-opds2-irradia"))
    implementation(project(":palace-opds2-parser-api"))
    implementation(project(":palace-parser-api"))
    implementation(project(":palace-presentableerror-api"))
    implementation(project(":palace-taskrecorder-api"))

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
