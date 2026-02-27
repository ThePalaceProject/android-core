dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-books-api"))
    implementation(project(":palace-books-registry-api"))
    implementation(project(":palace-feeds-api"))
    implementation(project(":palace-opds-core"))
    implementation(project(":palace-tenprint"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jnull)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.uri)
    implementation(libs.picasso)
    implementation(libs.slf4j)
}

android {
    defaultConfig {
        useLibrary("org.apache.http.legacy")
    }
}
