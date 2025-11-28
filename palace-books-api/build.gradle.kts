dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-json-core"))
    implementation(project(":palace-lcp"))
    implementation(project(":palace-opds-core"))

    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jnull)
    implementation(libs.io7m.junreachable)
    implementation(libs.irradia.mime.api)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jcip.annotations)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.audiobook.api)
    implementation(libs.palace.audiobook.manifest.api)
    implementation(libs.palace.drm.core)
    implementation(libs.palace.readium2.api)
    implementation(libs.r2.shared)
    implementation(libs.slf4j)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
