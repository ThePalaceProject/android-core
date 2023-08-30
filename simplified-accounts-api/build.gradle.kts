dependencies {
    implementation(project(":simplified-announcements"))
    implementation(project(":simplified-links"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-parser-api"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-taskrecorder-api"))

    implementation(libs.google.guava)
    implementation(libs.io7m.jfunctional)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.jackson.databind)
    implementation(libs.jcip.annotations)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.drm.core)
    implementation(libs.palace.http.api)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
