plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.google.guava)
    implementation(libs.io7m.jnull)
}
