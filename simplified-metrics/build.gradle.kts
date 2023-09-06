dependencies {
    implementation(project(":simplified-metrics-api"))

    implementation(libs.firebase.analytics)
    implementation(libs.play.services.measurement.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
}
