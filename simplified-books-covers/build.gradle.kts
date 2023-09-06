dependencies {
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-books-bundled-api"))
    implementation(project(":simplified-books-registry-api"))
    implementation(project(":simplified-feeds-api"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-tenprint"))

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
