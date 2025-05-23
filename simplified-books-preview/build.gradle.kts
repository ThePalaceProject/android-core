dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-books-audio"))
    implementation(project(":simplified-books-database-api"))
    implementation(project(":simplified-books-formats-api"))
    implementation(project(":simplified-books-registry-api"))
    implementation(project(":simplified-files"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-taskrecorder-api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.joda.time)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.drm.core)
    implementation(libs.palace.http.api)
    implementation(libs.palace.http.downloads)
    implementation(libs.slf4j)
}
