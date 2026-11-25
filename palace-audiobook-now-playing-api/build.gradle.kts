plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-books-api"))

    implementation(libs.io7m.jattribute.core)
    implementation(libs.kotlin.stdlib)
}
