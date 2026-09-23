plugins {
    id("org.thepalaceproject.build.aar")
}

android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "SIMPLIFIED_VERSION", "\"${rootProject.ext["VERSION_NAME"]}\"")
        buildConfigField("String", "ADOBE_DRM_PROVIDER_VERSION", "\"${libs.versions.palace.drm.adobe.get()}\"")
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-files"))
    implementation(project(":palace-json-core"))

    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.io7m.junreachable)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.palace.drm.core)
    implementation(libs.slf4j)
}
