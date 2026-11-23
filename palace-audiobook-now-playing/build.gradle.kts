plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-audiobook-now-playing-api"))
    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-books-api"))
    implementation(project(":palace-profiles-api"))
    implementation(project(":palace-profiles-controller-api"))
    implementation(project(":palace-threads"))

    implementation(libs.google.guava)
    implementation(libs.io7m.jattribute.core)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)
}
