dependencies {
    api(project(":simplified-buildconfig-api"))
    api(project(":simplified-ui-accounts"))
    api(project(":simplified-ui-catalog"))
    api(project(":simplified-ui-settings"))
    api(project(":simplified-ui-listeners-api"))

    implementation(project(":simplified-profiles-controller-api"))
    implementation(project(":simplified-ui-neutrality"))
    implementation(project(":simplified-viewer-api"))
    implementation(project(":simplified-viewer-spi"))

    api(libs.pandora.bottom.navigator)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.slf4j)
}
