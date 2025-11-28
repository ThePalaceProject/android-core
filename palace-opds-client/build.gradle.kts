dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-books-api"))
    implementation(project(":palace-feeds-api"))
    implementation(project(":palace-opds-core"))
    implementation(project(":palace-parser-api"))
    implementation(project(":palace-presentableerror-api"))

    implementation(libs.io7m.jattribute.core)
    implementation(libs.io7m.jmulticlose)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.slf4j)
}
