package org.nypl.simplified.tests.documents

import android.content.res.AssetManager
import com.google.common.util.concurrent.MoreExecutors
import one.irradia.mime.api.MIMEType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.librarysimplified.documents.DocumentConfiguration
import org.librarysimplified.documents.DocumentConfigurationServiceType
import org.librarysimplified.documents.DocumentStores
import org.librarysimplified.documents.DocumentType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPRequestType
import org.librarysimplified.http.api.LSHTTPResponseProperties
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.librarysimplified.http.api.LSHTTPResponseType
import org.mockito.Mockito
import org.nypl.simplified.tests.TestDirectories
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DocumentStoreTest {

  private val remoteURI = URI.create("https://www.thepalaceproject.org")

  private val documentWithName = DocumentConfiguration(
    name = "document.html",
    remoteURI = remoteURI
  )

  private val documentWithoutName = DocumentConfiguration(
    name = null,
    remoteURI = remoteURI
  )

  private val executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1))

  private val responseStatus = LSHTTPResponseStatus.Responded.OK(
    properties = LSHTTPResponseProperties(
      problemReport = null,
      status = 200,
      originalStatus = 200,
      message = "",
      contentType = MIMEType("text", "plain", mapOf()),
      contentLength = null,
      headers = mapOf(),
      cookies = listOf(),
      authorization = null
    ),
    bodyStream = InputStream.nullInputStream()
  )

  private lateinit var assetManager: AssetManager
  private lateinit var baseDirectory: File
  private lateinit var configuration: DocumentConfigurationServiceType
  private lateinit var http: LSHTTPClientType
  private lateinit var requestBuilderType: LSHTTPRequestBuilderType
  private lateinit var requestType: LSHTTPRequestType
  private lateinit var responseType: LSHTTPResponseType

  @BeforeEach
  fun setup() {
    assetManager = Mockito.mock(AssetManager::class.java)
    baseDirectory = TestDirectories.temporaryDirectory()
    configuration = Mockito.mock(DocumentConfigurationServiceType::class.java)
    http = Mockito.mock(LSHTTPClientType::class.java)
    requestBuilderType = Mockito.mock(LSHTTPRequestBuilderType::class.java)
    requestType = Mockito.mock(LSHTTPRequestType::class.java)
    responseType = Mockito.mock(LSHTTPResponseType::class.java)

    Mockito.`when`(assetManager.open(Mockito.anyString())).thenReturn(InputStream.nullInputStream())
    Mockito.`when`(http.newRequest(documentWithName.remoteURI)).thenReturn(requestBuilderType)
    Mockito.`when`(requestBuilderType.build()).thenReturn(requestType)
    Mockito.`when`(requestType.execute()).thenReturn(responseType)
    Mockito.`when`(responseType.status).thenReturn(responseStatus)
  }

  @Test
  fun testReadableUrl() {
    Mockito.`when`(configuration.about).thenReturn(documentWithoutName)
    Mockito.`when`(configuration.privacyPolicy).thenReturn(documentWithName)

    val documentsStore = DocumentStores.create(
      assetManager = assetManager,
      http = http,
      baseDirectory = baseDirectory,
      configuration = configuration
    )

    Assertions.assertTrue(documentsStore.about is DocumentType)
    Assertions.assertTrue((documentsStore.about as DocumentType).readableURL == remoteURI.toURL())

    Assertions.assertTrue(documentsStore.privacyPolicy is DocumentType)
    Assertions.assertTrue(
      (documentsStore.privacyPolicy as DocumentType).readableURL ==
        File(baseDirectory, documentWithName.name.orEmpty()).toURI().toURL()
    )
  }

  @Test
  fun testNoDocumentUpdates() {
    Mockito.`when`(configuration.about).thenReturn(documentWithoutName)
    Mockito.`when`(configuration.acknowledgements).thenReturn(documentWithoutName)
    Mockito.`when`(configuration.eula).thenReturn(documentWithoutName)
    Mockito.`when`(configuration.faq).thenReturn(documentWithoutName)
    Mockito.`when`(configuration.licenses).thenReturn(documentWithoutName)
    Mockito.`when`(configuration.privacyPolicy).thenReturn(documentWithoutName)

    val store = DocumentStores.create(
      assetManager = assetManager,
      http = http,
      baseDirectory = baseDirectory,
      configuration = configuration
    )

    store.update(executor)
      .get(5L, TimeUnit.SECONDS)

    Mockito.verify(http, Mockito.times(0)).newRequest(documentWithoutName.remoteURI)
  }

  @Test
  fun testOneDocumentUpdate() {
    Mockito.`when`(configuration.about).thenReturn(documentWithName)
    Mockito.`when`(configuration.acknowledgements).thenReturn(documentWithoutName)
    Mockito.`when`(configuration.eula).thenReturn(documentWithoutName)
    Mockito.`when`(configuration.faq).thenReturn(documentWithoutName)
    Mockito.`when`(configuration.licenses).thenReturn(documentWithoutName)
    Mockito.`when`(configuration.privacyPolicy).thenReturn(documentWithoutName)

    val store = DocumentStores.create(
      assetManager = assetManager,
      http = http,
      baseDirectory = baseDirectory,
      configuration = configuration
    )

    store.update(executor)
      .get(5L, TimeUnit.SECONDS)

    Mockito.verify(http, Mockito.times(1)).newRequest(documentWithName.remoteURI)
  }

  @Test
  fun testAllDocumentUpdates() {
    Mockito.`when`(configuration.about).thenReturn(documentWithName)
    Mockito.`when`(configuration.acknowledgements).thenReturn(documentWithName)
    Mockito.`when`(configuration.eula).thenReturn(documentWithName)
    Mockito.`when`(configuration.faq).thenReturn(documentWithName)
    Mockito.`when`(configuration.licenses).thenReturn(documentWithName)
    Mockito.`when`(configuration.privacyPolicy).thenReturn(documentWithName)

    val store = DocumentStores.create(
      assetManager = assetManager,
      http = http,
      baseDirectory = baseDirectory,
      configuration = configuration
    )

    store.update(executor)
      .get(5L, TimeUnit.SECONDS)

    // the faq document is not updated
    Mockito.verify(http, Mockito.times(5)).newRequest(documentWithName.remoteURI)
  }
}
