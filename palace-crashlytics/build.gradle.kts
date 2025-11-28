dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-crashlytics-api"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.firebase.crashlytics)
}
