dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-registry-api"))
    implementation(project(":simplified-accounts-source-spi"))
    implementation(project(":simplified-books-database-api"))
    implementation(project(":simplified-books-formats-api"))
    implementation(project(":simplified-presentableerror-api"))

    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jnull)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
