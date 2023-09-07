dependencies {
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-taskrecorder-api"))

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
