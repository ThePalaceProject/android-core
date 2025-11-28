dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-books-api"))
    implementation(project(":palace-books-registry-api"))
    implementation(project(":palace-opds-core"))
    implementation(project(":palace-presentableerror-api"))

    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)
}
