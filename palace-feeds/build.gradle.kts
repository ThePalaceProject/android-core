plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-feeds-api"))
    implementation(project(":palace-feeds-opds12"))
    implementation(project(":palace-feeds-opds20"))
    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-books-api"))
    implementation(project(":palace-books-formats-api"))
    implementation(project(":palace-links"))
    implementation(project(":palace-opds-core"))

    implementation(libs.irradia.mime.api)
    implementation(libs.joda.time)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.palace.webpub.core)
    implementation(libs.slf4j)
}
