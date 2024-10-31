dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-crashlytics-api"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.firebase.crashlytics)
}
