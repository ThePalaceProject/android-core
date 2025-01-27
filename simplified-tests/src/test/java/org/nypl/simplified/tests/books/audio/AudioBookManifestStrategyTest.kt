package org.nypl.simplified.tests.books.audio

import android.app.Application
import io.reactivex.Observable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_fulfill.api.ManifestFulfillmentStrategyRegistryType
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentError
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsersType
import org.librarysimplified.http.api.LSHTTPClientType
import org.mockito.Mockito
import org.nypl.simplified.books.audio.AudioBookLink
import org.nypl.simplified.books.audio.AudioBookManifestRequest
import org.nypl.simplified.books.audio.AudioBookStrategy
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.tests.MutableServiceDirectory
import org.nypl.simplified.tests.TestDirectories
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

class AudioBookManifestStrategyTest {

  private val logger =
    LoggerFactory.getLogger(AudioBookManifestStrategyTest::class.java)

  private lateinit var context: Application
  private lateinit var basicStrategies: ManifestFulfillmentBasicType
  private lateinit var basicStrategy: ManifestFulfillmentStrategyType
  private lateinit var fulfillError: ManifestFulfillmentError
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var manifestParsers: ManifestParsersType
  private lateinit var services: MutableServiceDirectory
  private lateinit var strategies: ManifestFulfillmentStrategyRegistryType
  private lateinit var tempFolder: File

  @BeforeEach
  fun testSetup() {
    this.context =
      Mockito.mock(Application::class.java)
    this.basicStrategy =
      Mockito.mock(ManifestFulfillmentStrategyType::class.java)
    this.basicStrategies =
      Mockito.mock(ManifestFulfillmentBasicType::class.java)
    this.strategies =
      Mockito.mock(ManifestFulfillmentStrategyRegistryType::class.java)
    this.manifestParsers =
      Mockito.mock(ManifestParsersType::class.java)
    this.httpClient =
      Mockito.mock(LSHTTPClientType::class.java)

    this.tempFolder =
      TestDirectories.temporaryDirectory()

    this.fulfillError =
      ManifestFulfillmentError(
        message = "Download failed!",
        extraMessages = listOf(),
        serverData = null
      )

    this.services = MutableServiceDirectory()
    this.services.putService(LSHTTPClientType::class.java, this.httpClient)
    this.services.putService(ManifestFulfillmentStrategyRegistryType::class.java, this.strategies)
  }

  @Test
  fun testNoBasicStrategyAvailable() {
    val strategy =
      AudioBookStrategy(
        context = this.context,
        request = AudioBookManifestRequest(
          cacheDirectory = File(tempFolder, "cache"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          credentials = null,
          httpClient = this.httpClient,
          isNetworkAvailable = { true },
          palaceID = PlayerPalaceID("6c15709a-b9cd-4eb8-815a-309f5d738a11"),
          services = this.services,
          strategyRegistry = this.strategies,
          target = AudioBookLink.Manifest(URI.create("http://www.example.com")),
          userAgent = PlayerUserAgent("test"),
        )
      )

    val failure = strategy.execute() as TaskResult.Failure
    Assertions.assertEquals(UnsupportedOperationException::class.java, failure.resolutionOf(0).exception?.javaClass)
  }

  @Test
  fun testNoBasicStrategyFails() {
    Mockito.`when`(
      this.strategies.findStrategy(
        this.any((ManifestFulfillmentBasicType::class.java)::class.java)
      )
    ).thenReturn(this.basicStrategies)

    Mockito.`when`(
      this.basicStrategies.create(
        this.any(ManifestFulfillmentBasicParameters::class.java)
      )
    ).thenReturn(this.basicStrategy)

    Mockito.`when`(this.basicStrategy.events)
      .thenReturn(Observable.never())

    val fulfillmentResult =
      PlayerResult.Failure<ManifestFulfilled, ManifestFulfillmentError>(this.fulfillError)

    Mockito.`when`(this.basicStrategy.execute())
      .thenReturn(fulfillmentResult)

    val strategy =
      AudioBookStrategy(
        context = this.context,
        request = AudioBookManifestRequest(
          target = AudioBookLink.Manifest(URI.create("http://www.example.com")),
          httpClient = this.httpClient,
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { true },
          strategyRegistry = this.strategies,
          palaceID = PlayerPalaceID("6c15709a-b9cd-4eb8-815a-309f5d738a11"),
          cacheDirectory = File(tempFolder, "cache")
        )
      )

    val failure = strategy.execute() as TaskResult.Failure

    Assertions.assertEquals("Download failed!", failure.resolutionOf(0).message)
  }

  @Test
  fun testNoBasicStrategyParseFails() {
    Mockito.`when`(
      this.strategies.findStrategy(
        this.any((ManifestFulfillmentBasicType::class.java)::class.java)
      )
    ).thenReturn(this.basicStrategies)

    Mockito.`when`(
      this.basicStrategies.create(
        this.any(ManifestFulfillmentBasicParameters::class.java)
      )
    ).thenReturn(this.basicStrategy)

    Mockito.`when`(this.basicStrategy.events)
      .thenReturn(Observable.never())

    val fulfillmentResult =
      PlayerResult.Success<ManifestFulfilled, ManifestFulfillmentError>(
        ManifestFulfilled(
          source = URI.create("http://www.example.com"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          authorization = null,
          data = ByteArray(23)
        )
      )

    Mockito.`when`(this.basicStrategy.execute())
      .thenReturn(fulfillmentResult)

    val strategy =
      AudioBookStrategy(
        context = this.context,
        request = AudioBookManifestRequest(
          target = AudioBookLink.Manifest(URI.create("http://www.example.com")),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          services = this.services,
          isNetworkAvailable = { true },
          strategyRegistry = this.strategies,
          manifestParsers = AudioBookFailingParsers,
          extensions = emptyList(),
          httpClient = this.httpClient,
          palaceID = PlayerPalaceID("6c15709a-b9cd-4eb8-815a-309f5d738a11"),
          cacheDirectory = File(tempFolder, "cache")
        )
      )

    val failure = strategy.execute() as TaskResult.Failure
    Assertions.assertTrue(
      failure.resolutionOf(1).message.startsWith("Manifest parsing failed")
    )
  }

  @Test
  fun testNoBasicStrategySucceeds() {
    Mockito.`when`(
      this.strategies.findStrategy(
        this.any((ManifestFulfillmentBasicType::class.java)::class.java)
      )
    ).thenReturn(this.basicStrategies)

    Mockito.`when`(
      this.basicStrategies.create(
        this.any(ManifestFulfillmentBasicParameters::class.java)
      )
    ).thenReturn(this.basicStrategy)

    Mockito.`when`(this.basicStrategy.events)
      .thenReturn(Observable.never())

    val fulfillmentResult =
      PlayerResult.Success<ManifestFulfilled, ManifestFulfillmentError>(
        ManifestFulfilled(
          source = URI.create("http://www.example.com"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          authorization = null,
          data = ByteArray(23)
        )
      )

    Mockito.`when`(this.basicStrategy.execute())
      .thenReturn(fulfillmentResult)

    val strategy =
      AudioBookStrategy(
        context = this.context,
        request = AudioBookManifestRequest(
          cacheDirectory = File(tempFolder, "cache"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          credentials = null,
          extensions = emptyList(),
          httpClient = this.httpClient,
          isNetworkAvailable = { true },
          licenseChecks = listOf(),
          manifestParsers = AudioBookSucceedingParsers,
          palaceID = PlayerPalaceID("6c15709a-b9cd-4eb8-815a-309f5d738a11"),
          services = this.services,
          strategyRegistry = this.strategies,
          target = AudioBookLink.Manifest(URI.create("http://www.example.com")),
          userAgent = PlayerUserAgent("test"),
        )
      )

    val success = strategy.execute() as TaskResult.Success
    Assertions.assertEquals(AudioBookSucceedingParsers.playerManifest, success.result.manifest)
  }

  @Test
  fun testNoNetworkLoadFails() {
    val strategy =
      AudioBookStrategy(
        context = this.context,
        request = AudioBookManifestRequest(
          cacheDirectory = File(tempFolder, "cache"),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          credentials = null,
          httpClient = this.httpClient,
          isNetworkAvailable = { false },
          palaceID = PlayerPalaceID("6c15709a-b9cd-4eb8-815a-309f5d738a11"),
          services = this.services,
          target = AudioBookLink.Manifest(URI.create("http://www.example.com")),
          userAgent = PlayerUserAgent("test"),
        )
      )

    val failure = strategy.execute() as TaskResult.Failure

    Assertions.assertEquals("No network is available, and no fallback data is available", failure.resolutionOf(0).message)
  }

  @Test
  fun testNoNetworkLoadSucceeds() {
    val strategy =
      AudioBookStrategy(
        context = this.context,
        request = AudioBookManifestRequest(
          target = AudioBookLink.Manifest(URI.create("http://www.example.com")),
          contentType = BookFormats.audioBookGenericMimeTypes().first(),
          userAgent = PlayerUserAgent("test"),
          credentials = null,
          loadFallbackData = {
            ManifestFulfilled(
              source = URI.create("http://www.example.com"),
              contentType = BookFormats.audioBookGenericMimeTypes().first(),
              authorization = null,
              data = ByteArray(23)
            )
          },
          services = this.services,
          manifestParsers = AudioBookSucceedingParsers,
          isNetworkAvailable = { false },
          strategyRegistry = this.strategies,
          licenseChecks = listOf(),
          httpClient = this.httpClient,
          palaceID = PlayerPalaceID("6c15709a-b9cd-4eb8-815a-309f5d738a11"),
          cacheDirectory = File(tempFolder, "cache")
        )
      )

    val success = strategy.execute() as TaskResult.Success
    Assertions.assertEquals(AudioBookSucceedingParsers.playerManifest, success.result.manifest)
  }

  /**
   * Some magic needed to mock calls via Kotlin.
   *
   * See: "https://stackoverflow.com/questions/49148801/mock-object-in-android-unit-test-with-kotlin-any-gives-null"
   */

  private fun <T> any(
    type: Class<T>
  ): T {
    Mockito.any(type)
    return this.uninitialized()
  }

  private fun <T> uninitialized(): T =
    null as T
}
