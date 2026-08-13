plugins {
    id("org.thepalaceproject.build.aar")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-books-api"))
    implementation(project(":palace-books-registry-api"))
    implementation(project(":palace-feeds-api"))
    implementation(project(":palace-links"))
    implementation(project(":palace-opds-core"))
    implementation(project(":palace-tenprint"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.common)
    implementation(libs.androidx.core.runtime)
    implementation(libs.glide.core)
    implementation(libs.glide.gifdecoder)
    implementation(libs.google.guava)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.okio)
    implementation(libs.palace.http.api)
    implementation(libs.palace.http.uri)
    implementation(libs.slf4j)
}
