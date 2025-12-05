dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-database-api"))
    implementation(project(":palace-accounts-json"))
    implementation(project(":palace-accounts-registry-api"))
    implementation(project(":palace-books-database"))
    implementation(project(":palace-books-database-api"))
    implementation(project(":palace-books-formats-api"))
    implementation(project(":palace-files"))
    implementation(project(":palace-json-core"))
    implementation(project(":palace-presentableerror-api"))
    implementation(project(":palace-taskrecorder-api"))

    implementation(libs.google.guava)
    implementation(libs.io7m.jfunctional)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jcip.annotations)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.rxjava2)
    implementation(libs.slf4j)

    compileOnly(libs.google.auto.value)
    annotationProcessor(libs.google.auto.value.processor)
}
