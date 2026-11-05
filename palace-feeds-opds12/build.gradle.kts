plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-feeds-api"))
    implementation(project(":palace-opds-core"))
    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-books-api"))
    implementation(project(":palace-books-formats-api"))
    implementation(project(":palace-content-api"))
    implementation(project(":palace-links"))

    implementation(libs.irradia.mime.api)
    implementation(libs.joda.time)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.slf4j)
}
