import java.util.Properties

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

fun propertyOptional(name: String): String? {
    val map = settings.extra
    if (map.has(name)) {
        return map[name] as String?
    }
    return null
}

fun property(name: String): String {
    return propertyOptional(name) ?: throw GradleException("Required property $name is not defined.")
}

fun propertyBooleanOptional(name: String, defaultValue: Boolean): Boolean {
    val value = propertyOptional(name) ?: return defaultValue
    return value.toBooleanStrict()
}

val adobeDRM =
    propertyBooleanOptional("org.thepalaceproject.adobeDRM.enabled", false)
val lcpDRM =
    propertyBooleanOptional("org.thepalaceproject.lcp.enabled", false)
val findawayDRM =
    propertyBooleanOptional("org.thepalaceproject.findaway.enabled", false)
val overdriveDRM =
    propertyBooleanOptional("org.thepalaceproject.overdrive.enabled", false)

println("DRM: org.thepalaceproject.adobeDRM.enabled  : $adobeDRM")
println("DRM: org.thepalaceproject.lcp.enabled       : $lcpDRM")
println("DRM: org.thepalaceproject.findaway.enabled  : $findawayDRM")
println("DRM: org.thepalaceproject.overdrive.enabled : $overdriveDRM")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("$rootDir/org.thepalaceproject.android.platform/build_libraries.toml"))
        }
    }

    /*
     * Conditionally enable access to S3.
     */

    val s3RepositoryEnabled: Boolean =
        propertyBooleanOptional("org.thepalaceproject.s3.depend", false)
    val s3RepositoryAccessKey: String? =
        propertyOptional("org.thepalaceproject.aws.access_key_id")
    val s3RepositorySecretKey: String? =
        propertyOptional("org.thepalaceproject.aws.secret_access_key")

    if (s3RepositoryEnabled) {
        if (s3RepositoryAccessKey == null) {
            throw GradleException(
                "If the org.thepalaceproject.s3.depend property is set to true, " +
                    "the org.thepalaceproject.aws.access_key_id property must be defined."
            )
        }
        if (s3RepositorySecretKey == null) {
            throw GradleException(
                "If the org.thepalaceproject.s3.depend property is set to true, " +
                    "the org.thepalaceproject.aws.secret_access_key property must be defined."
            )
        }
    }

    /*
     * Conditionally enable Adobe DRM.
     */

    if (adobeDRM && !s3RepositoryEnabled) {
        throw GradleException(
            "If the org.thepalaceproject.adobeDRM.enabled property is set to true, " +
                "the org.thepalaceproject.s3.depend property must be set to true."
        )
    }

    /*
     * Conditionally enable LCP DRM.
     */

    val lcpDRMEnabled: Boolean =
        propertyBooleanOptional("org.thepalaceproject.lcp.enabled", false)

    val credentialsPath =
        propertyOptional("org.thepalaceproject.app.credentials.palace")

    /*
     * The set of repositories used to resolve library dependencies. The order is significant!
     */

    repositories {

        /*
         * Enable access to $HOME/.m2/repository.
         */

        mavenLocal()

        /*
         * Enable access to Maven central.
         */

        mavenCentral()

        /*
         * Enable access to Google's maven repository.
         *
         * See https://maven.google.com/web/index.html
         */

        google()

        /*
         * Findaway access.
         */

        if (findawayDRM) {
            maven {
                url = uri("http://maven.findawayworld.com/artifactory/libs-release/")
                isAllowInsecureProtocol = true
            }
        }

        /*
         * Allow access to the Sonatype snapshots repository.
         */

        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }

        /*
         * Allow access to the Sonatype snapshots repository.
         */

        maven {
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        }

        /*
         * Allow access to Jitpack. This is used by, for example, Readium.
         */

        maven {
            url = uri("https://jitpack.io/")
        }

        /*
         * Optionally enable access to the S3 repository.
         */

        if (s3RepositoryEnabled) {
            maven {
                name = "S3 Snapshots"
                url = uri("s3://se-maven-repo/snapshots/")
                credentials(AwsCredentials::class) {
                    accessKey = s3RepositoryAccessKey
                    secretKey = s3RepositorySecretKey
                }
                mavenContent {
                    snapshotsOnly()
                }
            }

            maven {
                name = "S3 Releases"
                url = uri("s3://se-maven-repo/releases/")
                credentials(AwsCredentials::class) {
                    accessKey = s3RepositoryAccessKey
                    secretKey = s3RepositorySecretKey
                }
                mavenContent {
                    releasesOnly()
                }
            }
        }

        /*
         * Enable access to various credentials-gated elements.
         */

        if (lcpDRMEnabled) {
            val filePath: String =
                when (val lcpProfile = property("org.thepalaceproject.lcp.profile")) {
                    "prod", "test" -> {
                        "${credentialsPath}/LCP/Android/build_lcp_${lcpProfile}.properties"
                    }
                    else -> {
                        throw GradleException("Unrecognized LCP profile: $lcpProfile")
                    }
                }

            val lcpProperties = Properties()
            lcpProperties.load(File(filePath).inputStream())

            ivy {
                name = "LCP"
                url = uri(lcpProperties.getProperty("org.thepalaceproject.lcp.repositoryURI"))
                patternLayout {
                    artifact(lcpProperties.getProperty("org.thepalaceproject.lcp.repositoryLayout"))
                }
                metadataSources {
                    artifact()
                }
            }
        }

        /*
         * Obsolete dependencies.
         */

        jcenter()
    }
}

rootProject.name = "simplified"

include(":simplified-accessibility")
include(":simplified-accounts-api")
include(":simplified-accounts-database")
include(":simplified-accounts-database-api")
include(":simplified-accounts-json")
include(":simplified-accounts-registry")
include(":simplified-accounts-registry-api")
include(":simplified-accounts-source-filebased")
include(":simplified-accounts-source-nyplregistry")
include(":simplified-accounts-source-spi")
include(":simplified-adobe-extensions")
include(":simplified-analytics-api")
include(":simplified-analytics-circulation")
include(":simplified-android-ktx")
include(":simplified-announcements")
include(":simplified-app-palace")
include(":simplified-bookmarks")
include(":simplified-bookmarks-api")
include(":simplified-books-api")
include(":simplified-books-audio")
include(":simplified-books-borrowing")
include(":simplified-books-bundled-api")
include(":simplified-books-controller")
include(":simplified-books-controller-api")
include(":simplified-books-covers")
include(":simplified-books-database")
include(":simplified-books-database-api")
include(":simplified-books-formats")
include(":simplified-books-formats-api")
include(":simplified-books-preview")
include(":simplified-books-registry-api")
include(":simplified-books-time-tracking")
include(":simplified-boot-api")
include(":simplified-buildconfig-api")
include(":simplified-content-api")
include(":simplified-crashlytics")
include(":simplified-crashlytics-api")
include(":simplified-deeplinks-controller-api")
include(":simplified-documents")
include(":simplified-feeds-api")
include(":simplified-files")
include(":simplified-futures")
include(":simplified-json-core")
include(":simplified-lcp")
include(":simplified-links")
include(":simplified-links-json")
include(":simplified-main")
include(":simplified-mdc")
include(":simplified-metrics")
include(":simplified-metrics-api")
include(":simplified-migration-api")
include(":simplified-migration-spi")
include(":simplified-networkconnectivity")
include(":simplified-networkconnectivity-api")
include(":simplified-notifications")
include(":simplified-oauth")
include(":simplified-opds-auth-document")
include(":simplified-opds-auth-document-api")
include(":simplified-opds-core")
include(":simplified-opds2")
include(":simplified-opds2-irradia")
include(":simplified-opds2-parser-api")
include(":simplified-opds2-r2")
include(":simplified-parser-api")
include(":simplified-patron")
include(":simplified-patron-api")
include(":simplified-presentableerror-api")
include(":simplified-profiles")
include(":simplified-profiles-api")
include(":simplified-profiles-controller-api")
include(":simplified-reader-api")
include(":simplified-reports")
include(":simplified-services-api")
include(":simplified-taskrecorder-api")
include(":simplified-tenprint")
include(":simplified-tests")
include(":simplified-threads")
include(":simplified-ui-accounts")
include(":simplified-ui-announcements")
include(":simplified-ui-branding")
include(":simplified-ui-catalog")
include(":simplified-ui-errorpage")
include(":simplified-ui-images")
include(":simplified-ui-listeners-api")
include(":simplified-ui-navigation-tabs")
include(":simplified-ui-neutrality")
include(":simplified-ui-onboarding")
include(":simplified-ui-screen")
include(":simplified-ui-settings")
include(":simplified-ui-splash")
include(":simplified-ui-thread-api")
include(":simplified-ui-tutorial")
include(":simplified-viewer-api")
include(":simplified-viewer-audiobook")
include(":simplified-viewer-epub-readium2")
include(":simplified-viewer-pdf-pdfjs")
include(":simplified-viewer-preview")
include(":simplified-viewer-spi")
include(":simplified-webview")
