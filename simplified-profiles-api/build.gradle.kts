dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-database-api"))
    implementation(project(":simplified-feeds-api"))
    implementation(project(":simplified-reader-api"))

    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jnull)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.audiobook.api)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
