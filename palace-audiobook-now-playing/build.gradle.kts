plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-audiobook-now-playing-api"))
    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-books-api"))
    implementation(project(":palace-books-registry-api"))
    implementation(project(":palace-services-api"))
    implementation(project(":palace-threads"))
    implementation(project(":palace-viewer-audiobook"))
    implementation(project(":palace-viewer-spi"))

    implementation(libs.google.guava)
    implementation(libs.io7m.jattribute.core)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)
}
