plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-presentableerror-api"))

    implementation(libs.google.guava)
    implementation(libs.irradia.mime.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.slf4j)
}
