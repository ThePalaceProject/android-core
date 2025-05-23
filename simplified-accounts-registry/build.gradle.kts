dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-registry-api"))
    implementation(project(":simplified-accounts-source-spi"))
    implementation(project(":simplified-buildconfig-api"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-taskrecorder-api"))
    implementation(project(":simplified-threads"))

    implementation(libs.google.guava)
    implementation(libs.io7m.jattribute.core)
    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jmulticlose)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
