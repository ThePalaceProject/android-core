dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-books-audio"))
    implementation(project(":palace-books-database-api"))
    implementation(project(":palace-books-formats-api"))
    implementation(project(":palace-books-registry-api"))
    implementation(project(":palace-files"))
    implementation(project(":palace-opds-core"))
    implementation(project(":palace-presentableerror-api"))
    implementation(project(":palace-taskrecorder-api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.joda.time)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.drm.core)
    implementation(libs.palace.http.api)
    implementation(libs.palace.http.downloads)
    implementation(libs.slf4j)
}
