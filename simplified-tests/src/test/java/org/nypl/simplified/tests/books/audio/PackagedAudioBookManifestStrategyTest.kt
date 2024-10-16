package org.nypl.simplified.tests.books.audio

import android.app.Application
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_fulfill.api.ManifestFulfillmentStrategyRegistryType
import org.mockito.Mockito
import org.nypl.simplified.books.audio.AudioBookManifestRequest
import org.nypl.simplified.books.audio.PackagedAudioBookManifestStrategy
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.tests.MutableServiceDirectory
import java.io.File
import java.io.FileNotFoundException
import java.net.URI

class PackagedAudioBookManifestStrategyTest {
  private lateinit var context: Application
  private lateinit var services: MutableServiceDirectory
  private lateinit var strategies: ManifestFulfillmentStrategyRegistryType

  @TempDir
  @JvmField
  var tempDir: File? = null

  @BeforeEach
  fun testSetup() {
    this.context =
      Mockito.mock(Application::class.java)
    this.strategies =
      Mockito.mock(ManifestFulfillmentStrategyRegistryType::class.java)

    this.services = MutableServiceDirectory()
    this.services.putService(ManifestFulfillmentStrategyRegistryType::class.java, this.strategies)
  }

  @Test
  @Disabled("This test can no longer be run locally due to Readium's internal use of Android's Url class.")
  fun succeeds_whenTargetURIExistsInPackage() {
    val strategy =
      PackagedAudioBookManifestStrategy(
        context = this.context,
        request = AudioBookManifestRequest(
          file = getResource("bestnewhorror.zip"),
          targetURI = URI.create("manifest.json"),
          contentType = StandardFormatNames.lcpAudioBooks,
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { true },
          strategyRegistry = this.strategies,
          cacheDirectory = File(tempDir, "cache"),
          palaceID = PlayerPalaceID("6c15709a-b9cd-4eb8-815a-309f5d738a11")
        )
      )

    val success = strategy.execute() as TaskResult.Success
    Assertions.assertEquals("Best New Horror", success.result.manifest.metadata.title)
  }

  @Test
  @Disabled("This test can no longer be run locally due to Readium's internal use of Android's Url class.")
  fun fails_whenTargetURIDoesNotExistInPackage() {
    val strategy =
      PackagedAudioBookManifestStrategy(
        context = this.context,
        request = AudioBookManifestRequest(
          file = getResource("bestnewhorror.zip"),
          targetURI = URI.create("wrongfile.json"),
          contentType = StandardFormatNames.lcpAudioBooks,
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { true },
          strategyRegistry = this.strategies,
          cacheDirectory = File(tempDir, "cache"),
          palaceID = PlayerPalaceID("6c15709a-b9cd-4eb8-815a-309f5d738a11")
        )
      )

    val failure = strategy.execute() as TaskResult.Failure

    Assertions.assertEquals(
      "Unable to extract manifest from audio book file",
      failure.resolutionOf(0).message
    )
  }

  @Test
  fun fails_whenFileIsNull() {
    val strategy =
      PackagedAudioBookManifestStrategy(
        context = this.context,
        request = AudioBookManifestRequest(
          file = null,
          targetURI = URI.create("manifest.json"),
          contentType = StandardFormatNames.lcpAudioBooks,
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { true },
          strategyRegistry = this.strategies,
          cacheDirectory = File(tempDir, "cache"),
          palaceID = PlayerPalaceID("6c15709a-b9cd-4eb8-815a-309f5d738a11")
        )
      )

    val failure = strategy.execute() as TaskResult.Failure

    Assertions.assertEquals(
      "No audio book file",
      failure.resolutionOf(0).message
    )
  }

  private fun getResource(
    name: String
  ): File {
    val fileName =
      "/org/nypl/simplified/tests/books/audio/$name"

    val url =
      PackagedAudioBookManifestStrategyTest::class.java.getResource(fileName)
        ?: throw FileNotFoundException("No such resource: $fileName")

    return File(url.toURI())
  }
}
