dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-database-api"))
    implementation(project(":palace-profiles-api"))
    implementation(project(":palace-profiles-controller-api"))

    implementation(libs.androidx.core)
    implementation(libs.firebase.messaging)
    implementation(libs.irradia.mime.api)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.play.services.tasks)
    implementation(libs.slf4j)
}
