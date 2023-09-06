dependencies {
    implementation(project(":simplified-announcements"))
    implementation(project(":simplified-links"))
    implementation(project(":simplified-parser-api"))

    implementation(libs.google.guava)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
