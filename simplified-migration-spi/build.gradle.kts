dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-database-api"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-taskrecorder-api"))

    implementation(libs.google.guava)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
