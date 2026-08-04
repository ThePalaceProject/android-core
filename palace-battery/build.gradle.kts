plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-threads"))

    implementation(libs.io7m.jattribute.core)
    implementation(libs.slf4j)
}
