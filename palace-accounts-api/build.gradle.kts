dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-announcements"))
    implementation(project(":palace-links"))
    implementation(project(":palace-opds-core"))
    implementation(project(":palace-parser-api"))
    implementation(project(":palace-patron-api"))
    implementation(project(":palace-presentableerror-api"))
    implementation(project(":palace-taskrecorder-api"))

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
