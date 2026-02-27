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
val boundlessDRM =
    propertyBooleanOptional("org.thepalaceproject.boundless.enabled", false)

println("DRM: org.thepalaceproject.adobeDRM.enabled  : $adobeDRM")
println("DRM: org.thepalaceproject.boundless.enabled : $boundlessDRM")
println("DRM: org.thepalaceproject.findaway.enabled  : $findawayDRM")
println("DRM: org.thepalaceproject.lcp.enabled       : $lcpDRM")
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

    if (lcpDRMEnabled && !s3RepositoryEnabled) {
        throw GradleException(
            "If the org.thepalaceproject.lcp.enabled property is set to true, " +
                "the org.thepalaceproject.s3.depend property must also be set to true."
        )
    }

    /*
     * Conditionally enable Boundless DRM.
     */

    if (boundlessDRM && !s3RepositoryEnabled) {
        throw GradleException(
            "If the org.thepalaceproject.boundlessDRM.enabled property is set to true, " +
                "the org.thepalaceproject.s3.depend property must be set to true."
        )
    }

    /*
     * Conditionally enable Findaway DRM.
     */

    if (findawayDRM && !s3RepositoryEnabled) {
        throw GradleException(
            "If the org.thepalaceproject.findawayDRM.enabled property is set to true, " +
                "the org.thepalaceproject.s3.depend property must be set to true."
        )
    }

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
         * Allow access to the Sonatype snapshots repository.
         */

        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
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
    }
}

rootProject.name = "simplified"

include(":palace-accessibility")
include(":palace-accounts-api")
include(":palace-accounts-database")
include(":palace-accounts-database-api")
include(":palace-accounts-json")
include(":palace-accounts-registry")
include(":palace-accounts-registry-api")
include(":palace-accounts-source-filebased")
include(":palace-accounts-source-nyplregistry")
include(":palace-accounts-source-spi")
include(":palace-adobe-extensions")
include(":palace-analytics-api")
include(":palace-analytics-circulation")
include(":palace-announcements")
include(":palace-app-palace")
include(":palace-bookmarks")
include(":palace-bookmarks-api")
include(":palace-books-api")
include(":palace-books-audio")
include(":palace-books-borrowing")
include(":palace-books-bundled-api")
include(":palace-books-controller")
include(":palace-books-controller-api")
include(":palace-books-covers")
include(":palace-books-database")
include(":palace-books-database-api")
include(":palace-books-formats")
include(":palace-books-formats-api")
include(":palace-books-preview")
include(":palace-books-registry-api")
include(":palace-books-time-tracking")
include(":palace-boot-api")
include(":palace-buildconfig-api")
include(":palace-content-api")
include(":palace-crashlytics")
include(":palace-crashlytics-api")
include(":palace-db")
include(":palace-db-api")
include(":palace-documents")
include(":palace-feeds-api")
include(":palace-files")
include(":palace-futures")
include(":palace-json-core")
include(":palace-lcp")
include(":palace-links")
include(":palace-links-json")
include(":palace-mdc")
include(":palace-notifications")
include(":palace-opds-auth-document")
include(":palace-opds-auth-document-api")
include(":palace-opds-client")
include(":palace-opds-core")
include(":palace-opds2")
include(":palace-opds2-irradia")
include(":palace-opds2-parser-api")
include(":palace-opds2-pwp")
include(":palace-parser-api")
include(":palace-patron")
include(":palace-patron-api")
include(":palace-presentableerror-api")
include(":palace-profiles")
include(":palace-profiles-api")
include(":palace-profiles-controller-api")
include(":palace-reader-api")
include(":palace-reports")
include(":palace-services-api")
include(":palace-taskrecorder-api")
include(":palace-tenprint")
include(":palace-tests")
include(":palace-threads")
include(":palace-ui")
include(":palace-ui-bottomsheet")
include(":palace-ui-errorpage")
include(":palace-ui-images")
include(":palace-ui-screen")
include(":palace-viewer-api")
include(":palace-viewer-audiobook")
include(":palace-viewer-epub-readium2")
include(":palace-viewer-pdf-pdfjs")
include(":palace-viewer-preview")
include(":palace-viewer-spi")
include(":palace-webview")

include(":palace-sandbox")
