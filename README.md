The Palace Project Android Client
===

[![Build Status](https://img.shields.io/github/actions/workflow/status/ThePalaceProject/android-core/android-main.yml?branch=main)](https://github.com/ThePalaceProject/android-core/actions/workflows/android-main.yml)

Lyrasis fork of the NYPL's [Library Simplified](http://www.librarysimplified.org/) Android client.

![Palace](./src/site/resources/palace.jpg?raw=true)

### What Is This?

The contents of this repository provide the The Palace Project's Android client application, Palace.

|Application|Module|Description|
|-----------|------|-----------|
|Palace|[simplified-app-palace](simplified-app-palace)|The DRM-enabled application|

## Contents

* [Building](#building-the-code)
  * [The Short Version](#the-short-version)
  * [The Longer Version](#the-longer-version)
    * [Android SDK](#android-sdk)
    * [JDK](#jdk)
    * [S3 Credentials](#s3-credentials)
    * [APK Signing](#apk-signing)
    * [Enabling DRM](#enabling-drm)
    * [Adobe DRM](#adobe-drm-support)
    * [Findaway DRM](#findaway-audiobook-drm-support)
* [Development](#development)
  * [Branching/Merging](#branchingmerging)
  * [Project Structure](#project-structure--architecture)
    * [MVC](#mvc)
    * [MVVM](#mvvm)
    * [API vs SPI](#api-vs-spi)
    * [Modules](#modules)
  * [Binaries](#binaries)
  * [Ktlint](#ktlint)
* [Release Process](#release-process)
* [License](#license)

## Building The Code

#### Cloning the Repository

Make sure you clone this repository with `git clone --recursive`. 
If you forgot to use `--recursive`, then execute:

```
$ git submodule update --init
```

#### Android SDK

Install the [Android SDK and Android Studio](https://developer.android.com/studio/). We don't
support the use of any other IDE at the moment.

#### JDK

Install a reasonably modern JDK, at least JDK 17. We don't recommend building
on anything newer than the current LTS JDK for everyday usage.

Any of the following JDKs should work:

  * [OpenJDK](https://jdk.java.net/java-se-ri/17)
  * [Adoptium](https://adoptopenjdk.net/)
  * [Amazon Coretto](https://aws.amazon.com/corretto/)
  * [Zulu](https://www.azul.com/downloads/zulu-community/?package=jdk)

The `JAVA_HOME` environment variable must be set correctly. You can check what it is set to in
most shells with `echo $JAVA_HOME`. If that command does not show anything, adding the following
line to `$HOME/.profile` and then executing `source $HOME/.profile` or opening a new shell
should suffice:

~~~w
# Replace NNN with your particular version of 17.
export JAVA_HOME=/path/to/jdk-17+NNN
~~~

You can verify that everything is set up correctly by inspecting the results of both
`java -version` and `javac -version`:

~~~
$ java -version
openjdk version "17.0.8" 2023-07-18
OpenJDK Runtime Environment (build 17.0.8+7)
OpenJDK 64-Bit Server VM (build 17.0.8+7, mixed mode)
~~~

#### S3 Credentials

Our application can use packages that are only available from our
S3 bucket. If you wish to use these packages, you need to obtain
S3 credentials and then tell Gradle to use them.

S3 credentials can be obtained by emailing `Jonathan.Green@lyrasis.org`
or by asking in the `#palace-project-tech` channel of
[lyrasis.slack.com](https://lyrasis.slack.com).

Once you have your credentials, the following lines must be added to 
`$HOME/.gradle/gradle.properties`:

~~~
# Replace ACCESS_KEY and SECRET_ACCESS_KEY appropriately.
# Do NOT use quotes around either value.
org.thepalaceproject.aws.access_key_id=ACCESS_KEY
org.thepalaceproject.aws.secret_access_key=SECRET_ACCESS_KEY
org.thepalaceproject.s3.depend=true
~~~

#### APK signing

If you wish to generate a signed APK for publishing the application, you will need to copy
a keystore to `release.jks` and set the following values correctly in
`$HOME/.gradle/gradle.properties`:

~~~
# Replace KEYALIAS, KEYPASSWORD, and STOREPASSWORD appropriately.
# Do NOT use quotes around values.
org.thepalaceproject.keyAlias=KEYALIAS
org.thepalaceproject.keyPassword=KEYPASSWORD
org.thepalaceproject.storePassword=STOREPASSWORD
~~~

Note that APK files are only signed if the code is built in _release_ mode. In other words, you
need to use either of these commands to produce signed APK files:

~~~
$ ./gradlew clean assembleRelease test
$ ./gradlew clean assemble test
~~~

#### Enabling DRM

The application contains optional support for various DRM systems. These are all disabled by
default, and must be enabled explicitly in order to build a version of the [Palace](simplified-app-palace)
application equivalent to the Play Store version.

Firstly, make sure you have your [S3](#s3-credentials) credentials
correctly configured. Then, add the following property to your
`$HOME/.gradle/gradle.properties` file:

```
org.thepalaceproject.adobeDRM.enabled=true
```

This will instruct the build system that you want to build with DRM enabled.
If you were to attempt to build the code right now, you would encounter a
build failure: When DRM is enabled, the build system will check that you have
provided various configuration files containing secrets that the DRM systems
require, and will refuse to build the app if you've failed to do this. The
build system can copy in the correct secrets for you if tell it the location
of directories containing those secrets. There are two directories to configure:
A credentials directory, containing secrets needed to build the app, and an
assets directory, containing secrets needed by the app at runtime (which will be
installed as assets in the app). For example, assuming that you have
[Palace's](simplified-app-palace) credentials in '/path/to/palace/credentials',
and assets in `/path/to/palace/assets`, you can add the following properties to
your `$HOME/.gradle/gradle.properties` file and the build system will copy in
the required secrets at build time:

```
org.thepalaceproject.app.credentials.palace=/path/to/palace/credentials
org.thepalaceproject.app.assets.palace=/path/to/palace/assets
```

#### Adobe DRM Support

The project currently makes calls to the Palace [Adobe DRM
API](https://github.com/ThePalaceProject/android-drm-core). The API
is structured in a manner that means that enabling actual support
for Adobe DRM simply entails adding a dependency on the Palace Adobe
DRM _implementation_. This implementation is only available to DRM
licensees. Please get in touch with us if you have a DRM license and
want to produce a DRM-enabled build!

#### Findaway Audiobook DRM support

The project currently uses the Palace [AudioBook API](https://github.com/ThePalaceProject/android-audiobook)
to provide support for playing audio books. The API is structured such
that adding support for new types of audiobooks and playback engines
only involves adding those modules to the classpath. The Palace app depends on a Findaway
module to play Findaway audio books. Please get in touch with us if you have
a Findaway license and want to produce a Findaway-enabled build.

#### LCP DRM Support

The project uses Readium's liblcp module to provide support for LCP
content protection. This module must be available on the classpath
when the `org.thepalaceproject.lcp.enabled` property is true. Otherwise,
the project will not compile. Please get in touch with us if you have
an LCP license and want to produce a DRM-enabled build.

## Development

### Branching/Merging

All new features should be created on feature branches and merged to `main` once
completed.

### Project Structure / Architecture

#### MVC

The project, as a whole, roughly follows an [MVC](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93controller)
architecture distributed over the application modules. The _controller_ in the application is
task-based and executes all tasks on a background thread to avoid any possibility of blocking
the Android UI thread.

#### MVVM

Newer application modules, roughly follow an [MVVM](https://en.wikipedia.org/wiki/Model%E2%80%93view%E2%80%93viewmodel) architecture.
The _View Model_ in the application exposes reactive properties
and executes all tasks on a background thread. The _View_ observes those properties and updates on the Android UI thread.

#### API vs SPI

The project makes various references to _APIs_ and _SPIs_. _API_ stands for _application
programming interface_ and _SPI_ stands for _service provider interface_.

An _API_ module defines a user-visible contract (or _specification_) for a module; it defines the
data types and abstract interfaces via which the user is expected to make calls in order to make use of a
module. An API module is typically paired with an _implementation_ module that provides concrete
implementations of the API interface types. A good example of this is the accounts database: The
[Accounts database API](simplified-accounts-database-api) declares a set of data types and
interfaces that describe how an accounts database should behave. The [Accounts database](simplified-accounts-database)
_implementation_ module provides an implementation of the described API. Keeping the API
_specification_ strictly separated from the _implementation_ in this manner has a number of benefits:

* Substitutability: When an _API_ has a sufficiently detailed specification, it's possible to
  replace an implementation module with a superior implementation without having to modify
  code that makes calls to the API.

* Testability: Keeping API types strictly separated from implementation types tends to lead to
  interfaces that are easy to mock.

* Understandability: Users of modules can go straight to the _API_ specifications to find out
  how to use them. This cuts down on the amount of archaeological work necessary to learn how
  to use the application's internal interfaces.

An _SPI_ module is similar to an API in that it provides a specification, however the defined
interfaces are expected to be _implemented_ by users rather than _called_ by users directly. An
implementor of an SPI is known as a _service provider_.

A good example of an SPI is the [Account provider source SPI](simplified-accounts-source-spi); the SPI
defines an interface that is expected to be implemented by account provider sources. The
[file-based source](simplified-accounts-source-filebased) module is capable of delivering account
provider descriptions from a bundled asset file. The [registry source](simplified-accounts-source-nyplregistry)
implementation is capable of fetching account provider descriptions from the NYPL's registry
server. Neither the _SPI_ or the implementation modules are expected to be used by application
programmers directly: Instead, implementation modules are loaded using [ServiceLoader](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ServiceLoader.html)
by the [Account provider registry](simplified-accounts-registry), and users interact with the
registry via a [published registry API](simplified-accounts-registry-api). This same design
pattern is used by the [NYPL AudioBook API](https://github.com/NYPL-Simplified/audiobook-android)
to provide a common API into which new audio book players and parsers can be introduced _without
needing to modify application code at all_.

Modules should make every attempt not to specify explicit dependencies on _implementation_ modules.
API and implementation modules should typically only depend on other API modules, leaving the choice
of implementation modules to the final application assembly. In other words, a module should say
"I can work with any module that provides this API" rather than "I depend on implementation `M`
of a particular API". Following this convention allows us to replace module implementation without
having to modify lots of different parts of the application; it allows us to avoid
_strong coupling_ between modules.

Most of the modularity concepts described here were pioneered by the [OSGi module system](https://www.osgi.org/developer/modularity/)
and so, although the Library Simplified application is not an OSGi application, much of the
design and architecture conforms to conventions followed by OSGi applications. Further reading
can be found on the OSGi web site.

#### Build System

The build is driven by the [build.gradle](build.gradle) file in the root of the project,
with the `build.gradle` files in each module typically only listing dependencies (the actual
dependency definitions are defined in the root `build.gradle` file to avoid duplicating version
numbers over the whole project). Metadata used to publish builds (such as Maven group IDs, version
numbers, etc) is defined in the `gradle.properties` file in each module. The [gradle.properties](gradle.properties)
file in the root of the project defines default values that are overridden as necessary by each
module.

#### Test suite

We aggregate all unit tests in the [simplified-tests](simplified-tests) module. Tests should
be written using the JUnit 5 library, although at the time of writing we have [one test](simplified-tests/src/test/java/org/nypl/simplified/tests/webview/CookiesContract.kt)
that still requires JUnit 4 due to the use of [Robolectric](http://robolectric.org/).

#### Modules

The project is heavily modularized in order to keep the separate application components as loosely
coupled as possible.

|Module|Description|
|------|-----------|
|[org.librarysimplified.accessibility](simplified-accessibility)|Accessibility APIs and functionality|
|[org.librarysimplified.accounts.api](simplified-accounts-api)|Accounts API|
|[org.librarysimplified.accounts.database](simplified-accounts-database)|Accounts database implementation|
|[org.librarysimplified.accounts.database.api](simplified-accounts-database-api)|Accounts database API|
|[org.librarysimplified.accounts.json](simplified-accounts-json)|Shared JSON classes|
|[org.librarysimplified.accounts.registry](simplified-accounts-registry)|Account provider registry implementation|
|[org.librarysimplified.accounts.registry.api](simplified-accounts-registry-api)|Account provider registry API|
|[org.librarysimplified.accounts.source.nyplregistry](simplified-accounts-source-nyplregistry)|NYPL registry client implementation|
|[org.librarysimplified.accounts.source.spi](simplified-accounts-source-spi)|Account provider source SPI|
|[org.librarysimplified.adobe.extensions](simplified-adobe-extensions)|Adobe DRM convenience functions|
|[org.librarysimplified.analytics.api](simplified-analytics-api)|Analytics API|
|[org.librarysimplified.analytics.circulation](simplified-analytics-circulation)|Circulation manager analytics implementation|
|[org.librarysimplified.announcements](simplified-announcements)|Announcements API|
|[org.thepalaceproject.palace](simplified-app-palace)|Palace|
|[org.librarysimplified.bookmarks](simplified-bookmarks)|Bookmark service implementation|
|[org.librarysimplified.bookmarks.api](simplified-bookmarks-api)|Bookmarks service API|
|[org.librarysimplified.books.api](simplified-books-api)|Book types|
|[org.librarysimplified.books.audio](simplified-books-audio)|Audio book support code|
|[org.librarysimplified.books.borrowing](simplified-books-borrowing)|Book borrowing|
|[org.librarysimplified.books.bundled.api](simplified-books-bundled-api)|Bundled books API|
|[org.librarysimplified.books.controller](simplified-books-controller)|Books/Profiles controller implementation|
|[org.librarysimplified.books.controller.api](simplified-books-controller-api)|Books controller API|
|[org.librarysimplified.books.covers](simplified-books-covers)|Book cover loading and generation|
|[org.librarysimplified.books.database](simplified-books-database)|Book database implementation|
|[org.librarysimplified.books.database.api](simplified-books-database-api)|Book database API|
|[org.librarysimplified.books.formats](simplified-books-formats)|Book formats implementation|
|[org.librarysimplified.books.formats.api](simplified-books-formats-api)|Book formats API|
|[org.librarysimplified.books.preview](simplified-books-preview)|Book preview|
|[org.librarysimplified.books.registry.api](simplified-books-registry-api)|Book registry API|
|[org.librarysimplified.books.time.tracking](simplified-books-time-tracking)|Books time tracking|
|[org.librarysimplified.boot.api](simplified-boot-api)|Application boot API|
|[org.librarysimplified.buildconfig.api](simplified-buildconfig-api)|Build-time configuration API|
|[org.librarysimplified.content.api](simplified-content-api)|Content resolver API|
|[org.librarysimplified.crashlytics](simplified-crashlytics)|Crashlytics|
|[org.librarysimplified.crashlytics.api](simplified-crashlytics-api)|Crashlytics functionality|
|[org.librarysimplified.documents](simplified-documents)|Documents|
|[org.librarysimplified.feeds.api](simplified-feeds-api)|Feed API|
|[org.librarysimplified.files](simplified-files)|File utilities|
|[org.librarysimplified.futures](simplified-futures)|Guava Future extensions|
|[org.librarysimplified.json.core](simplified-json-core)|JSON utilities|
|[org.librarysimplified.lcp](simplified-lcp)|LCP content protection provider|
|[org.librarysimplified.links](simplified-links)|Link types|
|[org.librarysimplified.links.json](simplified-links-json)|Link JSON parsing|
|[org.librarysimplified.mdc](simplified-mdc)|MDC conventions|
|[org.librarysimplified.notifications](simplified-notifications)|Notification service|
|[org.librarysimplified.oauth](simplified-oauth)|OAuth|
|[org.librarysimplified.opds.auth_document](simplified-opds-auth-document)|OPDS authentication document parser implementation|
|[org.librarysimplified.opds.auth_document.api](simplified-opds-auth-document-api)|OPDS authentication document parser API|
|[org.librarysimplified.opds.client](simplified-opds-client)|Stateful OPDS client|
|[org.librarysimplified.opds.core](simplified-opds-core)|OPDS feed parser|
|[org.librarysimplified.opds2](simplified-opds2)|OPDS 2.0 model definitions|
|[org.librarysimplified.opds2.irradia](simplified-opds2-irradia)|OPDS 2.0 Parser [Irradia]|
|[org.librarysimplified.opds2.parser.api](simplified-opds2-parser-api)|OPDS 2.0 parser API|
|[org.librarysimplified.opds2.r2](simplified-opds2-r2)|OPDS 2.0 Parser [R2]|
|[org.librarysimplified.parser.api](simplified-parser-api)|Parser API|
|[org.librarysimplified.patron](simplified-patron)|Patron user profile parser implementation|
|[org.librarysimplified.patron.api](simplified-patron-api)|Patron user profile parser API|
|[org.librarysimplified.presentableerror.api](simplified-presentableerror-api)|Presentable error API|
|[org.librarysimplified.profiles](simplified-profiles)|Profile database implementation|
|[org.librarysimplified.profiles.api](simplified-profiles-api)|Profile database API|
|[org.librarysimplified.profiles.controller.api](simplified-profiles-controller-api)|Profile controller API|
|[org.librarysimplified.reader.api](simplified-reader-api)|Reader API types|
|[org.librarysimplified.reports](simplified-reports)|Error reporting|
|[org.librarysimplified.sandbox](simplified-sandbox)|Sandbox|
|[org.librarysimplified.services.api](simplified-services-api)|Application services API|
|[org.librarysimplified.taskrecorder.api](simplified-taskrecorder-api)|Task recorder API|
|[org.librarysimplified.tenprint](simplified-tenprint)|10PRINT implementation|
|[org.librarysimplified.tests](simplified-tests)|Test suite|
|[org.librarysimplified.threads](simplified-threads)|Thread utilities|
|[org.librarysimplified.ui](simplified-ui)|UI|
|[org.librarysimplified.ui.errorpage](simplified-ui-errorpage)|Error details screen|
|[org.librarysimplified.ui.images](simplified-ui-images)|Image loader API for general image resources|
|[org.librarysimplified.ui.screen](simplified-ui-screen)|Screen API|
|[org.librarysimplified.viewer.api](simplified-viewer-api)|Viewer API|
|[org.librarysimplified.viewer.audiobook](simplified-viewer-audiobook)|AudioBook viewer|
|[org.librarysimplified.viewer.epub.readium2](simplified-viewer-epub-readium2)|Readium 2 EPUB reader|
|[org.librarysimplified.viewer.pdf.pdfjs](simplified-viewer-pdf-pdfjs)|PDF reader|
|[org.librarysimplified.viewer.preview](simplified-viewer-preview)|Book preview viewer|
|[org.librarysimplified.viewer.spi](simplified-viewer-spi)|Viewer SPI|
|[org.librarysimplified.webview](simplified-webview)|WebView utilities|

_The above table is generated with [ReadMe.java](src/misc/ReadMe.java)._

### Binaries

Binaries for every commit are built and published in the [android-binaries](https://github.com/ThePalaceProject/android-binaries)
repository. Note that these binaries are _not_ to be considered production ready and may have
undergone little or no testing. Use at your own risk!

### Ktlint

The codebase uses [ktlint](https://ktlint.github.io/) to enforce a consistent
code style. It's possible to ensure that any changes you've made to the code
continue to pass `ktlint` checks by running the `ktlintFormat` task to reformat
source code:

```
$ ./gradlew ktlintFormat
```

## Release Process

Please see [RELEASING.md](RELEASING.md) for documentation on our release
process.

## License

~~~
Copyright 2015 The New York Public Library, Astor, Lenox, and Tilden Foundations

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy of the
License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed
under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.
~~~
