dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-database-api"))
    implementation(project(":palace-json-core"))
    implementation(project(":palace-links"))
    implementation(project(":palace-links-json"))
    implementation(project(":palace-parser-api"))
    implementation(project(":palace-patron-api"))
    implementation(project(":palace-taskrecorder-api"))

    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.http.api)
    implementation(libs.slf4j)
}
