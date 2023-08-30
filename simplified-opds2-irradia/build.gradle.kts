dependencies {
    implementation(project(":simplified-links"))
    implementation(project(":simplified-opds2"))
    implementation(project(":simplified-opds2-parser-api"))
    implementation(project(":simplified-parser-api"))

    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.opds2.api)
    implementation(libs.irradia.opds2.lexical)
    implementation(libs.irradia.opds2.librarysimplified)
    implementation(libs.irradia.opds2.parser.api)
    implementation(libs.irradia.opds2.parser.extension.spi)
    implementation(libs.irradia.opds2.parser.librarysimplified)
    implementation(libs.irradia.opds2.parser.vanilla)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j)
}
