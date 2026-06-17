plugins {
    id("org.thepalaceproject.build.jar")
}

dependencies {
    implementation(libs.irradia.mime.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.webpub.core)
    implementation(libs.slf4j)
}
