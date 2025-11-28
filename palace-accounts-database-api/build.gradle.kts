dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-registry-api"))
    implementation(project(":palace-accounts-source-spi"))
    implementation(project(":palace-books-database-api"))
    implementation(project(":palace-books-formats-api"))
    implementation(project(":palace-presentableerror-api"))

    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jnull)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
