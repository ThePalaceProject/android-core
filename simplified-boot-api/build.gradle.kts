dependencies {
    implementation(project(":simplified-presentableerror-api"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
