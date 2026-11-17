plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-database-api"))
    implementation(project(":palace-diagnostics-api"))
    implementation(project(":palace-profiles-api"))
    implementation(project(":palace-presentableerror-api"))
    implementation(project(":palace-profiles-controller-api"))
    implementation(project(":palace-taskrecorder-api"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)
}
