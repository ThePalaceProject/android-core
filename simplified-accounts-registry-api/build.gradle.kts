dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-taskrecorder-api"))

    implementation(libs.google.guava)
    implementation(libs.io7m.jattribute.core)
    implementation(libs.io7m.jfunctional)
    implementation(libs.jcip.annotations)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
