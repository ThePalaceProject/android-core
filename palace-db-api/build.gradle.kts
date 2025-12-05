dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-parser-api"))
    implementation(project(":palace-presentableerror-api"))

    implementation(libs.slf4j)
}
