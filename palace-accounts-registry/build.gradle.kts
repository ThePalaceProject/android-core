dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-registry-api"))
    implementation(project(":palace-accounts-source-spi"))
    implementation(project(":palace-buildconfig-api"))
    implementation(project(":palace-db-api"))
    implementation(project(":palace-presentableerror-api"))
    implementation(project(":palace-taskrecorder-api"))
    implementation(project(":palace-threads"))

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
