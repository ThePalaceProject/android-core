apply(plugin = "androidx.navigation.safeargs.kotlin")
apply(plugin = "kotlin-kapt")

android {
    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
}

dependencies {
    implementation(project(":simplified-accessibility"))
    implementation(project(":simplified-adobe-extensions"))
    implementation(project(":simplified-analytics-api"))
    implementation(project(":simplified-android-ktx"))
    implementation(project(":simplified-books-controller-api"))
    implementation(project(":simplified-boot-api"))
    implementation(project(":simplified-buildconfig-api"))
    implementation(project(":simplified-crashlytics-api"))
    implementation(project(":simplified-documents"))
    implementation(project(":simplified-oauth"))
    implementation(project(":simplified-profiles-controller-api"))
    implementation(project(":simplified-reports"))
    implementation(project(":simplified-services-api"))
    implementation(project(":simplified-threads"))
    implementation(project(":simplified-ui-errorpage"))
    implementation(project(":simplified-ui-images"))
    implementation(project(":simplified-ui-neutrality"))
    implementation(project(":simplified-ui-listeners-api"))
    implementation(project(":simplified-webview"))

    implementation(libs.androidx.app.compat)
    implementation(libs.androidx.constraint.layout)
    implementation(libs.androidx.recycler.view)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.drm.core)
    implementation(libs.rxandroid2)
    implementation(libs.rxjava2)
    implementation(libs.rxjava2.extensions)
    implementation(libs.slf4j)

    api(libs.androidx.preference)
}
