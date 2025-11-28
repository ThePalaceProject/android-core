dependencies {
    implementation(project(":palace-announcements"))
    implementation(project(":palace-json-core"))
    implementation(project(":palace-links"))
    implementation(project(":palace-links-json"))
    implementation(project(":palace-opds-auth-document-api"))
    implementation(project(":palace-parser-api"))

    implementation(libs.jackson.databind)
    implementation(libs.jackson.core)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
