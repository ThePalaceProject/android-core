dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-database-api"))
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-json-core"))
    implementation(project(":simplified-profiles-controller-api"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jnull)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.audiobook.api)
    implementation(libs.rxjava2)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
