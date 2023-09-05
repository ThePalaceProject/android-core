import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Properties

fun calculateVersionCode(): Int {
    val now = LocalDateTime.now(ZoneId.of("UTC"))
    val nowSeconds = now.toEpochSecond(ZoneOffset.UTC)
    // Seconds since 2021-03-15 09:20:00 UTC
    return (nowSeconds - 1615800000).toInt()
}

apply(plugin = "com.google.gms.google-services")
apply(plugin = "com.google.firebase.crashlytics")

/*
 * The asset files that are required to be present in order to build the app.
 */

val palaceAssetsRequired = Properties()

/*
 * The various DRM schemes require that some extra assets be present.
 */

val adobeDRM =
    project.findProperty("org.thepalaceproject.adobeDRM.enabled") == "true"
val lcpDRM =
    project.findProperty("org.thepalaceproject.lcp.enabled") == "true"
val findawayDRM =
    project.findProperty("org.thepalaceproject.findaway.enabled") == "true"
val overdriveDRM =
    project.findProperty("org.thepalaceproject.overdrive.enabled") == "true"

if (adobeDRM) {
    palaceAssetsRequired.setProperty(
        "assets/ReaderClientCert.sig",
        "b064e68b96e258e42fe1ca66ae3fc4863dd802c46585462220907ed291e1217d",
    )
}

if (adobeDRM || lcpDRM || findawayDRM || overdriveDRM) {
    palaceAssetsRequired.setProperty(
        "assets/secrets.conf",
        "5801d64987fb1eb2fb3e32a5bae1063aa2e444723bc89b8a1230117b631940b7",
    )
}

val palaceAssetsDirectory =
    project.findProperty("org.thepalaceproject.app.assets.palace") as String?

if (palaceAssetsDirectory != null) {
    val directory = File(palaceAssetsDirectory)
    if (!directory.isDirectory) {
        throw GradleException("The directory specified by org.thepalaceproject.app.assets.palace does not exist.")
    }
}

/*
 * A task that writes the required assets to a file in order to be used later by ZipCheck.
 */

fun createRequiredAssetsFile(file: File): Task {
    return task("CheckReleaseRequiredAssetsCreate") {
        doLast {
            file.writer().use {
                palaceAssetsRequired.store(it, "")
            }
        }
    }
}

/*
 * A task that executes ZipCheck against a given APK file and a list of required assets.
 */

fun createRequiredAssetsTask(
    checkFile: File,
    assetList: File,
): Task {
    return task("CheckReleaseRequiredAssets_${checkFile.name}", Exec::class) {
        commandLine = arrayListOf(
            "java",
            "$rootDir/org.thepalaceproject.android.platform/ZipCheck.java",
            "$checkFile",
            "$assetList",
        )
    }
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
            preBuildTask?.dependsOn?.add(requiredSigningTask)

            /*
             * For each APK output, create a task that checks that the APK contains the
             * required assets.
             */

            this.outputs.forEach {
                val outputFile = it.outputFile
                val assetFile = File("${project.buildDir}/required-assets.conf")
                val fileTask =
                    createRequiredAssetsFile(assetFile)
                val checkTask =
                    createRequiredAssetsTask(checkFile = outputFile, assetList = assetFile)

                checkTask.dependsOn.add(fileTask)
                this.assembleProvider.configure {
                    finalizedBy(checkTask)
                }
            }
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
    implementation(project(":simplified-accessibility"))
    implementation(project(":simplified-accounts-api"))
    implementation(project(":simplified-accounts-database"))
    implementation(project(":simplified-accounts-database-api"))
    implementation(project(":simplified-accounts-json"))
    implementation(project(":simplified-accounts-registry"))
    implementation(project(":simplified-accounts-registry-api"))
    implementation(project(":simplified-accounts-source-nyplregistry"))
    implementation(project(":simplified-accounts-source-spi"))
    implementation(project(":simplified-adobe-extensions"))
    implementation(project(":simplified-analytics-api"))
    implementation(project(":simplified-analytics-circulation"))
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
    implementation(project(":simplified-books-database"))
    implementation(project(":simplified-books-database-api"))
    implementation(project(":simplified-books-formats"))
    implementation(project(":simplified-books-formats-api"))
    implementation(project(":simplified-books-preview"))
    implementation(project(":simplified-books-registry-api"))
    implementation(project(":simplified-books-time-tracking"))
    implementation(project(":simplified-boot-api"))
    implementation(project(":simplified-buildconfig-api"))
    implementation(project(":simplified-content-api"))
    implementation(project(":simplified-crashlytics"))
    implementation(project(":simplified-crashlytics-api"))
    implementation(project(":simplified-deeplinks-controller-api"))
    implementation(project(":simplified-documents"))
    implementation(project(":simplified-feeds-api"))
    implementation(project(":simplified-files"))
    implementation(project(":simplified-futures"))
    implementation(project(":simplified-json-core"))
    implementation(project(":simplified-lcp"))
    implementation(project(":simplified-links"))
    implementation(project(":simplified-links-json"))
    implementation(project(":simplified-main"))
    implementation(project(":simplified-metrics"))
    implementation(project(":simplified-metrics-api"))
    implementation(project(":simplified-migration-api"))
    implementation(project(":simplified-migration-spi"))
    implementation(project(":simplified-networkconnectivity"))
    implementation(project(":simplified-networkconnectivity-api"))
    implementation(project(":simplified-notifications"))
    implementation(project(":simplified-oauth"))
    implementation(project(":simplified-opds2"))
    implementation(project(":simplified-opds2-irradia"))
    implementation(project(":simplified-opds2-parser-api"))
    implementation(project(":simplified-opds2-r2"))
    implementation(project(":simplified-opds-auth-document"))
    implementation(project(":simplified-opds-auth-document-api"))
    implementation(project(":simplified-opds-core"))
    implementation(project(":simplified-parser-api"))
    implementation(project(":simplified-patron"))
    implementation(project(":simplified-patron-api"))
    implementation(project(":simplified-presentableerror-api"))
    implementation(project(":simplified-profiles"))
    implementation(project(":simplified-profiles-api"))
    implementation(project(":simplified-profiles-controller-api"))
    implementation(project(":simplified-reader-api"))
    implementation(project(":simplified-reports"))
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
    implementation(project(":simplified-webview"))

    /*
     * Dependencies conditional upon Adobe DRM support.
     */

    if (adobeDRM) {
        implementation(libs.palace.drm.adobe)
    }

    /*
     * Dependencies conditional upon LCP support.
     */

    if (lcpDRM) {
        implementation(libs.readium.lcp) {
            artifact {
                type = "aar"
            }
        }
    }

    /*
     * Dependencies conditional upon Findaway support.
     */

    if (findawayDRM) {
        implementation(libs.palace.findaway)
        implementation(libs.findaway)
    }

    /*
     * Dependencies conditional upon Overdrive support.
     */

    if (overdriveDRM) {
        implementation(libs.palace.overdrive)
    }

    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.arch.core.common)
    implementation(libs.androidx.arch.core.runtime)
    implementation(libs.androidx.asynclayoutinflater)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout.core)
    implementation(libs.androidx.constraintlayout.solver)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.runtime)
    implementation(libs.androidx.cursoradapter)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.customview.poolingcontainer)
    implementation(libs.androidx.datastore.android)
    implementation(libs.androidx.datastore.core.android)
    implementation(libs.androidx.datastore.core.okio)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.emoji2.views.helper)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.interpolator)
    implementation(libs.androidx.legacy.support.core.ui)
    implementation(libs.androidx.legacy.support.core.utils)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.livedata.core)
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.loader)
    implementation(libs.androidx.loader)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.common.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.print)
    implementation(libs.androidx.recycler.view)
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.slidingpanelayout)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.transition)
    implementation(libs.androidx.transition.ktx)
    implementation(libs.androidx.vectordrawable)
    implementation(libs.androidx.versionedparcelable)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.annotations)
    implementation(libs.firebase.auth.interop)
    implementation(libs.firebase.common)
    implementation(libs.firebase.common.ktx)
    implementation(libs.firebase.components)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.datatransport)
    implementation(libs.firebase.dynamic.links)
    implementation(libs.firebase.dynamic.links.ktx)
    implementation(libs.firebase.encoders)
    implementation(libs.firebase.encoders.json)
    implementation(libs.firebase.encoders.proto)
    implementation(libs.firebase.installations)
    implementation(libs.firebase.installations.interop)
    implementation(libs.firebase.measurement.connector)
    implementation(libs.firebase.sessions)
    implementation(libs.google.failureaccess)
    implementation(libs.google.gson)
    implementation(libs.google.guava)
    implementation(libs.google.material)
    implementation(libs.inflationx.viewpump)
    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jnull)
    implementation(libs.irradia.fieldrush.api)
    implementation(libs.irradia.fieldrush.vanilla)
    implementation(libs.irradia.mime.api)
    implementation(libs.irradia.mime.vanilla)
    implementation(libs.irradia.opds2.api)
    implementation(libs.irradia.opds2.lexical)
    implementation(libs.irradia.opds2.librarysimplified)
    implementation(libs.irradia.opds2.parser.api)
    implementation(libs.irradia.opds2.parser.extension.spi)
    implementation(libs.irradia.opds2.parser.librarysimplified)
    implementation(libs.irradia.opds2.parser.vanilla)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.javax.inject)
    implementation(libs.joda.time)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.logback.android)
    implementation(libs.moznion.uribuildertiny)
    implementation(libs.nano.httpd)
    implementation(libs.nano.httpd.nanolets)
    implementation(libs.nypl.readium)
    implementation(libs.okhttp3)
    implementation(libs.okio)
    implementation(libs.palace.audiobook.api)
    implementation(libs.palace.audiobook.downloads)
    implementation(libs.palace.audiobook.feedbooks)
    implementation(libs.palace.audiobook.lcp)
    implementation(libs.palace.audiobook.manifest.api)
    implementation(libs.palace.audiobook.manifest.fulfill.api)
    implementation(libs.palace.audiobook.manifest.fulfill.basic)
    implementation(libs.palace.audiobook.manifest.fulfill.spi)
    implementation(libs.palace.audiobook.manifest.license.check.api)
    implementation(libs.palace.audiobook.manifest.parser.api)
    implementation(libs.palace.audiobook.manifest.parser.webpub)
    implementation(libs.palace.audiobook.open.access)
    implementation(libs.palace.audiobook.rbdigital)
    implementation(libs.palace.audiobook.views)
    implementation(libs.palace.drm.core)
    implementation(libs.palace.http.api)
    implementation(libs.palace.http.bearer.token)
    implementation(libs.palace.http.downloads)
    implementation(libs.palace.http.refresh.token)
    implementation(libs.palace.http.uri)
    implementation(libs.palace.http.vanilla)
    implementation(libs.palace.readium2.api)
    implementation(libs.palace.readium2.ui.thread)
    implementation(libs.palace.readium2.vanilla)
    implementation(libs.palace.readium2.views)
    implementation(libs.pandora.bottom.navigator)
    implementation(libs.picasso)
    implementation(libs.play.services.ads.identifier)
    implementation(libs.play.services.base)
    implementation(libs.play.services.basement)
    implementation(libs.play.services.measurement)
    implementation(libs.play.services.measurement.api)
    implementation(libs.play.services.measurement.base)
    implementation(libs.play.services.measurement.impl)
    implementation(libs.play.services.measurement.sdk)
    implementation(libs.play.services.measurement.sdk.api)
    implementation(libs.play.services.stats)
    implementation(libs.play.services.tasks)
    implementation(libs.r2.lcp)
    implementation(libs.r2.opds)
    implementation(libs.r2.shared)
    implementation(libs.r2.streamer)
    implementation(libs.reactive.streams)
    implementation(libs.rxandroid2)
    implementation(libs.rxjava)
    implementation(libs.rxjava2)
    implementation(libs.rxjava2.extensions)
    implementation(libs.slf4j)
    implementation(libs.transifex.common)
    implementation(libs.transifex.sdk)
    implementation(libs.transport.api)
    implementation(libs.transport.backend.cct)
    implementation(libs.transport.runtime)
}
