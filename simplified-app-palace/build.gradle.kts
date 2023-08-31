import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

fun calculateVersionCode(): Int {
    val now = LocalDateTime.now(ZoneId.of("UTC"))
    val nowSeconds = now.toEpochSecond(ZoneOffset.UTC)
    // Seconds since 2021-03-15 09:20:00 UTC
    return (nowSeconds - 1615800000).toInt()
}

/*
 * The asset files that are required to be present in order to build the app.
 */

val palaceAssetsDirectory =
    project.findProperty("org.thepalaceproject.app.assets.palace") as String?

if (palaceAssetsDirectory != null) {
    val directory = File(palaceAssetsDirectory)
    if (!directory.isDirectory) {
        throw GradleException("The directory specified by org.thepalaceproject.app.assets.palace does not exist.")
    }
}

val requiredAssetFiles = mapOf(
    Pair(
        "ReaderClientCert.sig",
        "b064e68b96e258e42fe1ca66ae3fc4863dd802c46585462220907ed291e1217d",
    ),
    Pair(
        "secrets.conf",
        "5801d64987fb1eb2fb3e32a5bae1063aa2e444723bc89b8a1230117b631940b7",
    ),
)

val requiredAssetsTask = task("CheckReleaseRequiredAssets") {
    // Nothing yet
}

/*
 * The signing information that is required to exist for release builds.
 */

val palaceKeyStore =
    File("$rootDir/release.jks")
val palaceKeyAlias =
    project.findProperty("org.thepalaceproject.keyAlias") as String?
val palaceKeyPassword =
    project.findProperty("org.thepalaceproject.keyPassword") as String?
val palaceStorePassword =
    project.findProperty("org.thepalaceproject.storePassword") as String?

val requiredSigningTask = task("CheckReleaseSigningInformation") {
    if (palaceKeyAlias == null) {
        throw GradleException("org.thepalaceproject.keyAlias is not specified.")
    }
    if (palaceKeyPassword == null) {
        throw GradleException("org.thepalaceproject.keyPassword is not specified.")
    }
    if (palaceStorePassword == null) {
        throw GradleException("org.thepalaceproject.storePassword is not specified.")
    }
}

android {
    defaultConfig {
        versionCode = calculateVersionCode()
        resourceConfigurations.add("en")
        resourceConfigurations.add("es")
        setProperty("archivesBaseName", "palace")
    }

    /*
     * Add the assets directory to the source sets. This is required for the various
     * secret files.
     */

    sourceSets {
        findByName("main")?.apply {
            if (palaceAssetsDirectory != null) {
                assets {
                    srcDir(palaceAssetsDirectory)
                }
            }
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols.add("lib/**/*.so")

            /*
             * Various components (R2, the PDF library, LCP, etc) include this shared library.
             */

            pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
            pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
            pickFirsts.add("lib/x86/libc++_shared.so")
            pickFirsts.add("lib/x86_64/libc++_shared.so")
        }
    }

    /*
     * Ensure that release builds are signed.
     */

    signingConfigs {
        create("release") {
            keyAlias = palaceKeyAlias
            keyPassword = palaceKeyPassword
            storeFile = palaceKeyStore
            storePassword = palaceStorePassword
        }
    }

    /*
     * Ensure that the right NDK ABIs are declared.
     */

    buildTypes {
        debug {
            ndk {
                abiFilters.add("x86")
                abiFilters.add("arm64-v8a")
                abiFilters.add("armeabi-v7a")
            }
            versionNameSuffix = "-debug"
        }
        release {
            ndk {
                abiFilters.add("arm64-v8a")
                abiFilters.add("armeabi-v7a")
            }
            signingConfigs.add(signingConfigs.getByName("release"))
        }
    }

    /*
     * Release builds need extra checking.
     */

    applicationVariants.all {
        if (this.buildType.name == "release") {
            val preBuildTask = tasks.findByName("preReleaseBuild")
            preBuildTask?.dependsOn?.add(requiredAssetsTask)
            preBuildTask?.dependsOn?.add(requiredSigningTask)
        }
    }
}

/*
 * Produce an AAB file whenever someone asks for "assemble".
 */

afterEvaluate {
    tasks.findByName("assemble")
        ?.dependsOn?.add(tasks.findByName("bundle"))
}

dependencies {
    implementation(project(":simplified-accounts-source-nyplregistry"))
    implementation(project(":simplified-analytics-circulation"))
    implementation(project(":simplified-crashlytics"))

    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.nypl.readium)
    implementation(libs.palace.drm.adobe)
    implementation(libs.palace.findaway)
    implementation(libs.palace.overdrive)
    implementation(libs.r2.lcp)
    implementation(libs.r2.opds)
    implementation(libs.r2.shared)
    implementation(libs.r2.streamer)

    implementation(libs.readium.lcp) {
        artifact {
            type = "aar"
        }
    }

    /*
     * All of the "core" dependencies.
     */

    implementation(project(":simplified-accessibility"))
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-database"))
    implementation(project(":simplified-accounts-database-api"))
    implementation(project(":simplified-accounts-json"))
    implementation(project(":simplified-accounts-registry"))
    implementation(project(":simplified-accounts-registry-api"))
    implementation(project(":simplified-accounts-source-spi"))
    implementation(project(":simplified-adobe-extensions"))
    implementation(project(":simplified-analytics-api"))
    implementation(project(":simplified-android-ktx"))
    implementation(project(":simplified-announcements"))
    implementation(project(":simplified-bookmarks"))
    implementation(project(":simplified-bookmarks-api"))
    implementation(project(":simplified-books-api"))
    implementation(project(":simplified-books-audio"))
    implementation(project(":simplified-books-borrowing"))
    implementation(project(":simplified-books-bundled-api"))
    implementation(project(":simplified-books-controller"))
    implementation(project(":simplified-books-controller-api"))
    implementation(project(":simplified-books-covers"))
    implementation(project(":simplified-books-formats"))
    implementation(project(":simplified-books-formats-api"))
    implementation(project(":simplified-books-registry-api"))
    implementation(project(":simplified-books-time-tracking"))
    implementation(project(":simplified-boot-api"))
    implementation(project(":simplified-buildconfig-api"))
    implementation(project(":simplified-content-api"))
    implementation(project(":simplified-crashlytics-api"))
    implementation(project(":simplified-deeplinks-controller-api"))
    implementation(project(":simplified-documents"))
    implementation(project(":simplified-feeds-api"))
    implementation(project(":simplified-files"))
    implementation(project(":simplified-lcp"))
    implementation(project(":simplified-main"))
    implementation(project(":simplified-metrics"))
    implementation(project(":simplified-metrics-api"))
    implementation(project(":simplified-migration-api"))
    implementation(project(":simplified-migration-spi"))
    implementation(project(":simplified-networkconnectivity"))
    implementation(project(":simplified-networkconnectivity-api"))
    implementation(project(":simplified-notifications"))
    implementation(project(":simplified-oauth"))
    implementation(project(":simplified-opds-auth-document"))
    implementation(project(":simplified-opds-auth-document-api"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-patron"))
    implementation(project(":simplified-patron-api"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-profiles"))
    implementation(project(":simplified-profiles-api"))
    implementation(project(":simplified-profiles-controller-api"))
    implementation(project(":simplified-reader-api"))
    implementation(project(":simplified-services-api"))
    implementation(project(":simplified-taskrecorder-api"))
    implementation(project(":simplified-tenprint"))
    implementation(project(":simplified-threads"))
    implementation(project(":simplified-ui-accounts"))
    implementation(project(":simplified-ui-announcements"))
    implementation(project(":simplified-ui-branding"))
    implementation(project(":simplified-ui-catalog"))
    implementation(project(":simplified-ui-errorpage"))
    implementation(project(":simplified-ui-images"))
    implementation(project(":simplified-ui-listeners-api"))
    implementation(project(":simplified-ui-navigation-tabs"))
    implementation(project(":simplified-ui-neutrality"))
    implementation(project(":simplified-ui-onboarding"))
    implementation(project(":simplified-ui-screen"))
    implementation(project(":simplified-ui-settings"))
    implementation(project(":simplified-ui-splash"))
    implementation(project(":simplified-ui-thread-api"))
    implementation(project(":simplified-ui-tutorial"))
    implementation(project(":simplified-viewer-api"))
    implementation(project(":simplified-viewer-audiobook"))
    implementation(project(":simplified-viewer-epub-readium2"))
    implementation(project(":simplified-viewer-pdf-pdfjs"))
    implementation(project(":simplified-viewer-preview"))
    implementation(project(":simplified-viewer-spi"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.app.compat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraint.layout)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.savedstate)
    implementation(libs.firebase.dynamic.links)
    implementation(libs.firebase.dynamic.links.ktx)
    implementation(libs.google.failureaccess)
    implementation(libs.google.guava)
    implementation(libs.google.material)
    implementation(libs.google.play.services.tasks)
    implementation(libs.io7m.jnull)
    implementation(libs.irradia.mime.api)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.joda.time)
    implementation(libs.kotlin.stdlib)
    implementation(libs.logback.android)
    implementation(libs.palace.audiobook.feedbooks)
    implementation(libs.palace.drm.core)
    implementation(libs.palace.http.api)
    implementation(libs.palace.http.bearer.token)
    implementation(libs.palace.http.vanilla)
    implementation(libs.pandora.bottom.navigator)
    implementation(libs.picasso)
    implementation(libs.r2.lcp)
    implementation(libs.rxandroid2)
    implementation(libs.rxjava2)
    implementation(libs.rxjava2.extensions)
    implementation(libs.slf4j)
    implementation(libs.transifex.sdk)
}
