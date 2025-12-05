dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-json"))
    implementation(project(":palace-db-api"))
    implementation(project(":palace-links"))
    implementation(project(":palace-parser-api"))
    implementation(project(":palace-presentableerror-api"))

    implementation(libs.google.protobuf)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jdk8)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.joda.time)

    // SQLite
    implementation(libs.io7m.anethum.api)
    implementation(libs.io7m.blackthorne.core)
    implementation(libs.io7m.blackthorne.jxe)
    implementation(libs.io7m.jaffirm.core)
    implementation(libs.io7m.jattribute.core)
    implementation(libs.io7m.jlexing.core)
    implementation(libs.io7m.junreachable)
    implementation(libs.io7m.jxe.core)
    implementation(libs.io7m.seltzer.api)
    implementation(libs.io7m.trasco.api)
    implementation(libs.io7m.trasco.vanilla)
    implementation(libs.io7m.trasco.xml.schemas)
    implementation(libs.xerces)
    implementation(libs.xerial.sqlite)

    implementation(libs.slf4j)
}
