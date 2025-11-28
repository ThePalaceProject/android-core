dependencies {
    implementation(project(":palace-announcements"))
    implementation(project(":palace-links"))
    implementation(project(":palace-parser-api"))

    implementation(libs.google.guava)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
