dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-database-api"))
    implementation(project(":palace-feeds-api"))
    implementation(project(":palace-reader-api"))

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
