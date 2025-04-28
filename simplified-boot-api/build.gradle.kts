dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-presentableerror-api"))

    implementation(libs.io7m.jattribute.core)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)
}
