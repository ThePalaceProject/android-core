dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-books-api"))
    implementation(project(":palace-opds-core"))
    implementation(project(":palace-presentableerror-api"))
    implementation(project(":palace-taskrecorder-api"))

    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.junreachable)
    implementation(libs.irradia.mime.api)
    implementation(libs.joda.time)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
