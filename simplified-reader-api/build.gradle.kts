dependencies {
    implementation(project(":simplified-json-core"))

    implementation(libs.io7m.jnull)
    implementation(libs.io7m.junreachable)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
