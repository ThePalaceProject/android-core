dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-feeds-api"))
    implementation(project(":simplified-parser-api"))
    implementation(project(":simplified-presentableerror-api"))

    implementation(libs.io7m.jattribute.core)
    implementation(libs.io7m.jmulticlose)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.slf4j)
}
