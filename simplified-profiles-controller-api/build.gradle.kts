dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-database-api"))
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-feeds-api"))
    implementation(project(":simplified-profiles-api"))
    implementation(project(":simplified-taskrecorder-api"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
