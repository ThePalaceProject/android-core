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
val boundlessDRM =
    project.findProperty("org.thepalaceproject.boundless.enabled") == "true"

if (adobeDRM) {
    palaceAssetsRequired.setProperty(
        "assets/ReaderClientCert.sig",
        "b064e68b96e258e42fe1ca66ae3fc4863dd802c46585462220907ed291e1217d",
    )
}

if (adobeDRM || lcpDRM || findawayDRM || overdriveDRM || boundlessDRM) {
    palaceAssetsRequired.setProperty(
        "assets/secrets.conf",
        "221db5c8c1ce1ddbc4f4c1a017f5b63271518d2adf6991010c2831a58b7f88ed",
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

val versionNameText =
    project.findProperty("VERSION_NAME") as String

android {
    defaultConfig {
        versionName = versionNameText
        versionCode = calculateVersionCode()
        resourceConfigurations.add("en")
        resourceConfigurations.add("de")
        resourceConfigurations.add("es")
        resourceConfigurations.add("fr")
        resourceConfigurations.add("it")
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
            this.signingConfig = signingConfigs.getByName("release")
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
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(project(":palace-accessibility"))
    implementation(project(":palace-accounts-api"))
    implementation(project(":palace-accounts-database"))
    implementation(project(":palace-accounts-database-api"))
    implementation(project(":palace-accounts-json"))
    implementation(project(":palace-accounts-registry"))
    implementation(project(":palace-accounts-registry-api"))
    implementation(project(":palace-accounts-source-nyplregistry"))
    implementation(project(":palace-accounts-source-spi"))
    implementation(project(":palace-adobe-extensions"))
    implementation(project(":palace-analytics-api"))
    implementation(project(":palace-analytics-circulation"))
    implementation(project(":palace-announcements"))
    implementation(project(":palace-bookmarks"))
    implementation(project(":palace-bookmarks-api"))
    implementation(project(":palace-books-api"))
    implementation(project(":palace-books-audio"))
    implementation(project(":palace-books-borrowing"))
    implementation(project(":palace-books-bundled-api"))
    implementation(project(":palace-books-controller"))
    implementation(project(":palace-books-controller-api"))
    implementation(project(":palace-books-covers"))
    implementation(project(":palace-books-database"))
    implementation(project(":palace-books-database-api"))
    implementation(project(":palace-books-formats"))
    implementation(project(":palace-books-formats-api"))
    implementation(project(":palace-books-preview"))
    implementation(project(":palace-books-registry-api"))
    implementation(project(":palace-books-time-tracking"))
    implementation(project(":palace-boot-api"))
    implementation(project(":palace-buildconfig-api"))
    implementation(project(":palace-content-api"))
    implementation(project(":palace-crashlytics"))
    implementation(project(":palace-crashlytics-api"))
    implementation(project(":palace-db"))
    implementation(project(":palace-db-api"))
    implementation(project(":palace-documents"))
    implementation(project(":palace-feeds-api"))
    implementation(project(":palace-files"))
    implementation(project(":palace-futures"))
    implementation(project(":palace-json-core"))
    implementation(project(":palace-lcp"))
    implementation(project(":palace-links"))
    implementation(project(":palace-links-json"))
    implementation(project(":palace-mdc"))
    implementation(project(":palace-notifications"))
    implementation(project(":palace-oauth"))
    implementation(project(":palace-opds-auth-document"))
    implementation(project(":palace-opds-auth-document-api"))
    implementation(project(":palace-opds-client"))
    implementation(project(":palace-opds-core"))
    implementation(project(":palace-opds2"))
    implementation(project(":palace-opds2-irradia"))
    implementation(project(":palace-opds2-parser-api"))
    implementation(project(":palace-opds2-r2"))
    implementation(project(":palace-parser-api"))
    implementation(project(":palace-patron"))
    implementation(project(":palace-patron-api"))
    implementation(project(":palace-presentableerror-api"))
    implementation(project(":palace-profiles"))
    implementation(project(":palace-profiles-api"))
    implementation(project(":palace-profiles-controller-api"))
    implementation(project(":palace-reader-api"))
    implementation(project(":palace-reports"))
    implementation(project(":palace-services-api"))
    implementation(project(":palace-taskrecorder-api"))
    implementation(project(":palace-tenprint"))
    implementation(project(":palace-threads"))
    implementation(project(":palace-ui"))
    implementation(project(":palace-ui-bottomsheet"))
    implementation(project(":palace-ui-errorpage"))
    implementation(project(":palace-ui-images"))
    implementation(project(":palace-ui-screen"))
    implementation(project(":palace-viewer-api"))
    implementation(project(":palace-viewer-audiobook"))
    implementation(project(":palace-viewer-epub-readium2"))
    implementation(project(":palace-viewer-pdf-pdfjs"))
    implementation(project(":palace-viewer-preview"))
    implementation(project(":palace-viewer-spi"))
    implementation(project(":palace-webview"))

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
        implementation(libs.palace.liblcp) {
            artifact {
                type = "aar"
            }
        }
    }

    /*
     * Dependencies conditional upon Findaway support.
     */

    if (findawayDRM) {
        implementation(libs.palace.audiobook.audioengine)

        // Findaway transitive dependencies.
        implementation(libs.dagger)
        implementation(libs.exoplayer2.core)
        implementation(libs.findaway)
        implementation(libs.findaway.common)
        implementation(libs.findaway.listening)
        implementation(libs.findaway.persistence)
        implementation(libs.findaway.play.android)
        implementation(libs.google.gson)
        implementation(libs.javax.inject)
        implementation(libs.koin.android)
        implementation(libs.koin.core)
        implementation(libs.koin.core.jvm)
        implementation(libs.moshi)
        implementation(libs.moshi.adapters)
        implementation(libs.moshi.kotlin)
        implementation(libs.okhttp3)
        implementation(libs.okhttp3.logging.interceptor)
        implementation(libs.retrofit2)
        implementation(libs.retrofit2.adapter.rxjava)
        implementation(libs.retrofit2.converter.gson)
        implementation(libs.retrofit2.converter.moshi)
        implementation(libs.rxandroid)
        implementation(libs.rxjava)
        implementation(libs.rxrelay)
        implementation(libs.sqlbrite)
        implementation(libs.stately.common)
        implementation(libs.stately.concurrency)
        implementation(libs.timber)
    }

    /*
     * Dependencies conditional upon Overdrive support.
     */

    if (overdriveDRM) {
        implementation(libs.palace.overdrive)
    }

    /*
     * Dependencies conditional upon Boundless DRM support.
     */

    if (boundlessDRM) {
        implementation(libs.palace.drm.boundless.core)
        implementation(libs.palace.drm.boundless.readium)
        implementation(libs.palace.drm.boundless.service)
    }

    /*
     * Dependencies needed for Feedbooks JWT handling. Always enabled.
     */

    implementation(libs.nimbus.jose.jwt)
    implementation(libs.net.minidev.json.smart)
    implementation(libs.net.minidev.accessors.smart)

    implementation(libs.media3.common)
    implementation(libs.media3.container)
    implementation(libs.media3.datasource)
    implementation(libs.media3.decoder)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.extractor)
    implementation(libs.media3.session)

    // Transifex
    implementation(libs.transifex.common)
    implementation(libs.transifex.sdk)
    implementation(libs.b3nedikt.viewpump)

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
    implementation(libs.androidx.core.splashscreen)
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
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.loader)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.media)
    implementation(libs.androidx.paging.common)
    implementation(libs.androidx.paging.common.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.print)
    implementation(libs.androidx.recycler.view)
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
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
    implementation(libs.androidx.vectordrawable.animated)
    implementation(libs.androidx.versionedparcelable)
    implementation(libs.androidx.viewpager)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)

    implementation(libs.azam.ulidj)
    implementation(libs.commons.compress)
    implementation(libs.commons.io)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.annotations)
    implementation(libs.firebase.common)
    implementation(libs.firebase.components)
    implementation(libs.firebase.config.interop)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.datatransport)
    implementation(libs.firebase.encoders)
    implementation(libs.firebase.encoders.json)
    implementation(libs.firebase.encoders.proto)
    implementation(libs.firebase.iid.interop)
    implementation(libs.firebase.installations)
    implementation(libs.firebase.installations.interop)
    implementation(libs.firebase.measurement.connector)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.sessions)
    implementation(libs.google.exoplayer)
    implementation(libs.google.failureaccess)
    implementation(libs.google.gson)
    implementation(libs.google.guava)
    implementation(libs.google.material)
    implementation(libs.google.protobuf)
    implementation(libs.io7m.jattribute.core)
    implementation(libs.io7m.jfunctional)
    implementation(libs.io7m.jmulticlose)
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
    implementation(libs.jackson.datatype.jdk8)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.javax.inject)
    implementation(libs.joda.time)
    implementation(libs.jsoup)
    implementation(libs.kabstand)
    implementation(libs.koi.core)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core.jvm)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.core.jvm)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.json.jvm)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.logback.android)
    implementation(libs.moznion.uribuildertiny)
    implementation(libs.nano.httpd)
    implementation(libs.nano.httpd.nanolets)
    implementation(libs.okhttp3)
    implementation(libs.okio)
    implementation(libs.palace.audiobook.api)
    implementation(libs.palace.audiobook.downloads)
    implementation(libs.palace.audiobook.feedbooks)
    implementation(libs.palace.audiobook.http)
    implementation(libs.palace.audiobook.json.canon)
    implementation(libs.palace.audiobook.json.web.token)
    implementation(libs.palace.audiobook.lcp.downloads)
    implementation(libs.palace.audiobook.lcp.license.status)
    implementation(libs.palace.audiobook.license.check.api)
    implementation(libs.palace.audiobook.license.check.spi)
    implementation(libs.palace.audiobook.manifest.api)
    implementation(libs.palace.audiobook.manifest.fulfill.api)
    implementation(libs.palace.audiobook.manifest.fulfill.basic)
    implementation(libs.palace.audiobook.manifest.fulfill.opa)
    implementation(libs.palace.audiobook.manifest.fulfill.spi)
    implementation(libs.palace.audiobook.manifest.parser.api)
    implementation(libs.palace.audiobook.manifest.parser.extension.spi)
    implementation(libs.palace.audiobook.manifest.parser.webpub)
    implementation(libs.palace.audiobook.media3)
    implementation(libs.palace.audiobook.parser.api)
    implementation(libs.palace.audiobook.persistence)
    implementation(libs.palace.audiobook.time.tracking)
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
    implementation(libs.palace.theme)
    implementation(libs.pdfium.android)
    implementation(libs.picasso)
    implementation(libs.play.services.ads.identifier)
    implementation(libs.play.services.base)
    implementation(libs.play.services.basement)
    implementation(libs.play.services.cloud.messaging)
    implementation(libs.play.services.location)
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
    implementation(libs.stduritemplate)
    implementation(libs.timber)
    implementation(libs.transport.api)
    implementation(libs.transport.backend.cct)
    implementation(libs.transport.runtime)

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
    implementation(libs.io7m.xyloid.natives)
    implementation(libs.xerces)
    implementation(libs.xerial.sqlite)
}
