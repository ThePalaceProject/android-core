android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "SIMPLIFIED_VERSION", "\"${rootProject.ext["VERSION_NAME"]}\"")
    }
}

dependencies {
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-files"))
    implementation(project(":simplified-json-core"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jnull)
    implementation(libs.io7m.junreachable)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.drm.core)
    implementation(libs.slf4j)
}
