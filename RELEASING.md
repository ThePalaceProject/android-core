### Releasing

We currently push releases to [Maven Central](https://search.maven.org)
and [Firebase](https://firebase.google.com), with tagged release builds
being pushed directly to the [Play Store](https://play.google.com) using
[Fastlane](https://fastlane.tools/).

The vast majority of the process is automated by our 
[CI scripts](https://github.com/NYPL-Simplified/Simplified-Android-CI), but
some manual steps are still required in order to trigger CI builds.

The instructions in this file detail the process for producing a
hypothetical version `99.0.0` release.

#### Make Sure Submodules Are Up-To-Date

The app requires a couple of submodules to build correctly. If you
cloned the repository with `--recursive`, then you likely already have
those submodules. However, it's best to run the following commands just
to make sure:

```
$ git submodule init
$ git submodule update --remote --recursive
```

#### Prepare To Create A Release Branch

The first step required is to set the project version numbers to the values
that they should be _after_ the current release. Why is this necessary? Well,
consider the following diagram of what _NOT_ to do:

![Bad Commits](./src/site/resources/commitsBad.png?raw=true)

In commit `a`, the app version number is `99.0.0-SNAPSHOT`. The developer then
creates a release branch in commit `b` and sets the version number to
`99.0.0`. Meanwhile, a developer commits `c` to the `develop` branch.
This sequence commits mean the following CI builds are produced in this
order:

  1. A build marked as `99.0.0-SNAPSHOT` from commit `a`.
  2. A build marked as `99.0.0` from commit `b`.
  3. A build marked as `99.0.0-SNAPSHOT` from commit `c`.

This has the effect of confusing people who are trying to test the builds:
_"Why have I just gotten a new build of `99.0.0-SNAPSHOT` when we're about
to test `99.0.0`?"_.

Instead, this is what _should_ have happened:

![Good Commits](./src/site/resources/commits.png?raw=true)

In commit `a`, the app version number is `99.0.0-SNAPSHOT`. The developer then
creates a commit `b` on the `develop` branch that sets the version number
to the expected _next_ release version `99.1.0-SNAPSHOT`. It doesn't matter
if the next release will actually be called `99.1.0-SNAPSHOT` or something
else; it just matters that it isn't called `99.0.0-SNAPSHOT` and isn't
called `99.0.0`. The developer _then_ creates a release branch in commit `c`
that sets the version number to `99.0.0`. At any point, another developer
working on the `develop` branch can safely create a commit `d` that
will have the correct version number already set thanks to the commit `c`.

This sequence commits mean the following CI builds are produced in this
order:

  1. A build marked as `99.0.0-SNAPSHOT` from commit `a`.
  2. A build marked as `99.1.0-SNAPSHOT` from commit `b`.
  3. A build marked as `99.0.0` from commit `c`.
  4. A build marked as `99.1.0-SNAPSHOT` from commit `d`.

Essentially, we need to leave things things set up so that developers can 
keep working on `develop` whilst we go on to make changes afterwards on
a separate release branch.

##### Update Version Numbers

So, firstly, set the application version numbers to the _next_ release, `99.1.0-SNAPSHOT`, 
as described above. This is done by editing `gradle.properties`.

```
# Make sure we're on the main branch
$ git branch
main

# Make sure the version numbers are what we expect to see.
$ grep VERSION_NAME gradle.properties
VERSION_NAME=98.0.0

# Update the version numbers.
$ $EDITOR gradle.properties
<... edit VERSION_NAME to 99.1.0-SNAPSHOT ...>

# Add the version files to be staged for the next commit.
$ git add gradle.properties
```

Don't commit yet: There's more work to do!

##### Close The Changelog

We currently use [changelog](https://www.io7m.com/software/changelog/) to
maintain a humanly-readable list of changes made between releases. The
[changelog manual](https://www.io7m.com/software/changelog/documentation/index.xhtml#d2e143)
has a detailed usage guide, but the release process only involves a couple
of commands. The CI builds generate release notes by looking at the contents
of the `README-CHANGES.xml` file in the root of the repository.

First, [set the release version](https://www.io7m.com/software/changelog/documentation/index.xhtml#id_f79aa94b-4dc7-44ee-823e-f6d1e3e8f155)
to the version we're trying to release now. In our case, that's `99.0.0`:

```
$ changelog release-set-version --version 99.0.0
```

Take a look at the current changelog and make sure it has all the entries you
expect to see:

```
$ changelog write-plain
```

Then, [close the current release](https://www.io7m.com/software/changelog/documentation/index.xhtml#id_31fe1fbf-62b9-4811-93dd-252a9ebfb222).
This marks the changelog as being finalized for the current release:

```
$ changelog release-finish
$ git add README-CHANGES.xml
```

##### Commit!

Commit all of your changes and push:

```
$ git status
Changes staged for commit:
  $app/gradle.properties
  README-CHANGES.xml

$ git commit -m 'Mark version 99.1.0-SNAPSHOT'
$ git push
```

#### Create A Release Branch

Now, create a release branch for the `99.0.0` release.

Use `release/99.0.0` as the branch name.


```
$ git checkout -b release/99.0.0
```

This creates a new `release/99.0.0` branch to which various commits
may be made to increment version numbers, update change logs, run
any last test builds, etc.

#### Set The Release Version

Set the app version to `99.0.0` by editing `gradle.properties`.

```
# Make sure we're on the release branch
$ git branch
release/99.0.0

# Make sure the version numbers are what we expect to see.
$ grep VERSION_NAME gradle.properties
VERSION_NAME=99.1.0-SNAPSHOT

# Update the version numbers.
$ $EDITOR gradle.properties
<... edit VERSION_NAME to 99.0.0 ...>

$ git add gradle.properties
```

<!--
#### Verify Library Dependencies

Run `.ci/ci-check-versions.sh` to check if all library dependencies are
referencing the latest versions:

```
$ .ci/ci-check-versions.sh
All of the checked libraries are up-to-date.
31 libraries were checked. 101 libraries were ignored.
```
-->

#### Commit And Push

```
$ git status
Changes staged for commit:
  $app/gradle.properties

$ git commit -m 'Start 0.99.0 release'
$ git push --all
```

#### Wait For QA To Test

At this point, the CI process will produce a release candidate build of `0.99.0`.
If there are issues with the build, then fixes _MUST_ be commited to the `main`
branch and _NOT_ the `release/0.99.0` branch. Individual fixes should then be merged
from `develop` into `release/0.99.0` and _NEVER_ in the opposite direction:

```
$ git branch
release/99.0.0

$ git merge develop --no-ff

$ changelog change-add --summary 'Something was fixed' --ticket 'SMA-18238'
$ git add README-CHANGES.xml
$ git commit -m 'Update changelog'

$ git push
```

Each time new commits are pushed, a new release candidate will be built by the CI.

#### Tag and Publish the Final Release

When QA are satisfied that a build is working, it's time to create the final
tag, and publish releases to GitHub and the Play Store. This is automated by the
Create Release workflow in GitHub.

1. In GitHub, open the Actions tab, and select the "Create Release" workflow.
2. Open the "Run workflow" dropdown.
3. In the "Use workflow from" dropdown, select the `release/99.0.0` branch.
4. Click the "Run workflow" button.

The workflow will tag the commit, and create a GitHub release with generated release
notes. It will produce a release build, and push it to the Play Store, with a link to the
release notes in GitHub. It will also push various components to Maven Central.

## Epilogue

If you've followed all of the steps above, then the release is completed. You don't
need to continue reading unless you want to know how the release process works behind
the scenes.

#### How Does Pushing To Maven Central Work?

We currently use the [brooklime](https://www.io7m.com/software/brooklime)
tool to do reliable Maven Central deployments. This documentation
makes references to a `brooklime.jar` file, and this should be understood
to be an abbreviation for whichever is the current release of the `brooklime`
command-line tool. At the time of writing, the exact jar file is
[com.io7m.brooklime.cmdline-0.1.0-main.jar](https://repo1.maven.org/maven2/com/io7m/brooklime/com.io7m.brooklime.cmdline/0.1.0/com.io7m.brooklime.cmdline-0.1.0-main.jar).

We generally rely on CI to do this for us, but it can also be performed
manually by developers on their local machine if necessary. See the
[.ci/ci-deploy-central-release.sh](ci-deploy-central-release.sh) script
to see how the commands described below are used by the CI system to upload
builds.

##### Building And Deploying

First, build the code as normal using `./gradlew clean assemble`.

Then, deploy the artifacts that will be uploaded to Maven Central into
a directory using the `org.librarysimplified.directory.publish` Gradle
property:

```
$ ./gradlew -Porg.librarysimplified.directory.publish="$HOME/tmp/simplified" publishAllPublicationsToDirectoryRepository
```

The above command uses a directory `$HOME/tmp/simplified` to contain
the binaries that will be uploaded to Maven Central for the current
release. You can use whatever directory you like, but you should use
a fresh directory for each release to avoid re-uploading artifacts from
older releases that may have been left hanging around.

##### Creating A Staging Repository

Then, using the `brooklime` tool, create a new staging repository:

```
$ java -jar brooklime.jar \
  create \
  --description 'Simplified X.Y.Z' \
  --stagingProfileId af061f5afba777 \
  --user MyMavenCentralUser \
  --password MyMavenCentralPassword

orglibrarysimplified-1087
```

The above command creates a new staging repository in the project's
staging profile (`af061f5afba777`). The `X.Y.Z` string should be
replaced with the version number of the release you are deploying,
and the `MyMavenCentralUser` and `MyMavenCentralPassword` strings
should be your Maven Central username and password, respectively. The
command will print the name of the new staging repository when
execution completes. In this case, it printed `orglibrarysimplified-1087`,
but the exact value will differ each time a new repository is created.

##### Uploading Content To The Staging Repository

```
$ java -jar brooklime.jar \
  upload \
  --stagingProfileId af061f5afba777 \
  --repository orglibrarysimplified-1087 \
  --user MyMavenCentralUser \
  --password MyMavenCentralPassword \
  --directory "$HOME/tmp/simplified"
```

The above command uploads the contents of the directory that we
populated during the build, to the staging repository `orglibrarysimplified-1087`
that we created in the previous step. The uploading step will typically
upload around two thousand files, and will generally take around ten
minutes to complete.

##### Closing And Releasing The Staging Repository

```
$ java -jar brooklime.jar \
  close \
  --stagingProfileId af061f5afba777 \
  --repository orglibrarysimplified-1087 \
  --user MyMavenCentralUser \
  --password MyMavenCentralPassword
  
$ java -jar brooklime.jar \
  release \
  --stagingProfileId af061f5afba777 \
  --repository orglibrarysimplified-1087 \
  --user MyMavenCentralUser \
  --password MyMavenCentralPassword
```

The two commands above will _close_ and then _release_ the staging
repository `orglibrarysimplified-1087`. This completes the deployment
process.

As closing and releasing a repository can take some time to complete,
it's sometimes reassuring to be able to observe the state of the staging
process. In the `Staging Repositories` tab in the [Sonatype web interface](https://oss.sonatype.org),
you will be able to see output such as this:

![Sonatype staging](./src/site/resources/stages.png?raw=true)

When the _release_ phase completes successfully, artifacts will be
permanently visible on Maven Central within 15 minutes. The artifacts
will appear in [search results](https://search.maven.org) within an hour.

