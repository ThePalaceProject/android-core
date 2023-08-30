package org.nypl.simplified.tests.http.refresh_token.borrow

import android.content.Context
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.joda.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestConstants
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.drm.core.AdobeAdeptLoan
import org.nypl.drm.core.AdobeDeviceID
import org.nypl.drm.core.AdobeLoanID
import org.nypl.drm.core.AdobeUserID
import org.nypl.drm.core.AdobeVendorID
import org.nypl.drm.core.AxisNowFulfillment
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobeClientToken
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePostActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationAdobePreActivationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountAuthenticationTokenInfo
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_database.BookDRMInformationHandleLCP
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatusEvent
import org.nypl.simplified.books.borrowing.internal.BorrowACSM
import org.nypl.simplified.books.borrowing.internal.BorrowAxisNow
import org.nypl.simplified.books.borrowing.internal.BorrowDirectDownload
import org.nypl.simplified.books.borrowing.internal.BorrowLCP
import org.nypl.simplified.books.borrowing.internal.BorrowLoanCreate
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSAcquisitionPath
import org.nypl.simplified.opds.core.OPDSAcquisitionPathElement
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.taskrecorder.api.TaskRecorder
import org.nypl.simplified.taskrecorder.api.TaskRecorderType
import org.nypl.simplified.tests.MutableServiceDirectory
import org.nypl.simplified.tests.TestDirectories
import org.nypl.simplified.tests.books.borrowing.BorrowTestFeeds
import org.nypl.simplified.tests.mocking.MockAccount
import org.nypl.simplified.tests.mocking.MockAccountProviders
import org.nypl.simplified.tests.mocking.MockAdobeAdeptConnector
import org.nypl.simplified.tests.mocking.MockAdobeAdeptExecutor
import org.nypl.simplified.tests.mocking.MockAdobeAdeptNetProvider
import org.nypl.simplified.tests.mocking.MockAdobeAdeptResourceProvider
import org.nypl.simplified.tests.mocking.MockAxisNowService
import org.nypl.simplified.tests.mocking.MockBookDatabase
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntry
import org.nypl.simplified.tests.mocking.MockBookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.tests.mocking.MockBorrowContext
import org.nypl.simplified.tests.mocking.MockBundledContentResolver
import org.nypl.simplified.tests.mocking.MockContentResolver
import org.nypl.simplified.tests.mocking.MockDRMInformationACSHandle
import org.nypl.simplified.tests.mocking.MockDRMInformationAxisHandle
import org.nypl.simplified.tests.mocking.MockLCPService
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BorrowBookRefreshTokenTest {

  @TempDir
  @JvmField
  var tempDir: File? = null

  private lateinit var account: MockAccount
  private lateinit var accountID: AccountID
  private lateinit var bookDatabase: BookDatabaseType
  private lateinit var bookDatabaseEntry: MockBookDatabaseEntry
  private lateinit var bookEvents: MutableList<BookStatusEvent>
  private lateinit var bookFormatSupport: BookFormatSupportType
  private lateinit var bookID: BookID
  private lateinit var bookRegistry: BookRegistryType
  private lateinit var bookStates: MutableList<BookStatus>
  private lateinit var bundledContent: MockBundledContentResolver
  private lateinit var contentResolver: MockContentResolver
  private lateinit var context: MockBorrowContext
  private lateinit var httpClient: LSHTTPClientType
  private lateinit var services: MutableServiceDirectory
  private lateinit var taskRecorder: TaskRecorderType
  private lateinit var webServer: MockWebServer

  private val logger = LoggerFactory.getLogger(BorrowBookRefreshTokenTest::class.java)

  @BeforeEach
  fun testSetup() {
    this.accountID =
      AccountID.generate()
    this.account = MockAccount(this.accountID)

    val credentials = AccountAuthenticationCredentials.BasicToken(
      userName = AccountUsername("1234"),
      password = AccountPassword("5678"),
      authenticationTokenInfo = AccountAuthenticationTokenInfo(
        accessToken = "abcd",
        authURI = URI("https://www.authrefresh.com")
      ),
      adobeCredentials = AccountAuthenticationAdobePreActivationCredentials(
        vendorID = AdobeVendorID("vendor"),
        clientToken = AccountAuthenticationAdobeClientToken(
          userName = "user",
          password = "password",
          rawToken = "b85e7fd7-cf6e-4e39-8da6-8df8c9ee9779"
        ),
        deviceManagerURI = URI.create("http://www.example.com"),
        postActivationCredentials = AccountAuthenticationAdobePostActivationCredentials(
          deviceID = AdobeDeviceID("ca887d21-a56c-4314-811e-952d885d2115"),
          userID = AdobeUserID("19b25c06-8b39-4643-8813-5980bee45651")
        )
      ),
      authenticationDescription = "BasicToken",
      annotationsURI = URI("https://www.example.com")
    )

    this.account.setLoginState(AccountLoginState.AccountLoggedIn(credentials))

    this.webServer = MockWebServer()
    this.webServer.start(20000)

    this.taskRecorder =
      TaskRecorder.create()
    this.contentResolver =
      MockContentResolver()
    this.bundledContent =
      MockBundledContentResolver()
    this.bookFormatSupport =
      Mockito.mock(BookFormatSupportType::class.java)
    this.bookRegistry =
      BookRegistry.create()
    this.bookStates =
      mutableListOf()
    this.bookEvents =
      mutableListOf()
    this.services = MutableServiceDirectory().apply {
      putService(
        OPDSFeedParserType::class.java,
        OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())
      )
    }

    val androidContext =
      Mockito.mock(Context::class.java)

    this.httpClient =
      LSHTTPClients()
        .create(
          context = androidContext,
          configuration = LSHTTPClientConfiguration(
            applicationName = "simplified-tests",
            applicationVersion = "999.999.0",
            tlsOverrides = null,
            timeout = Pair(5L, TimeUnit.SECONDS)
          )
        )

    val initialFeedEntry =
      BorrowTestFeeds.opdsLoanedFeedEntryOfType(
        this.webServer,
        StandardFormatNames.genericEPUBFiles.fullType
      )
    this.bookID =
      BookIDs.newFromOPDSEntry(initialFeedEntry)

    val bookInitial =
      Book(
        id = this.bookID,
        account = this.accountID,
        cover = null,
        thumbnail = null,
        entry = initialFeedEntry,
        formats = listOf()
      )

    this.bookDatabase =
      MockBookDatabase(this.accountID)
    this.bookDatabaseEntry =
      MockBookDatabaseEntry(bookInitial)

    this.context =
      MockBorrowContext(
        logger = this.logger,
        bookRegistry = this.bookRegistry,
        bundledContent = this.bundledContent,
        temporaryDirectory = TestDirectories.temporaryDirectory(),
        account = this.account,
        clock = { Instant.now() },
        httpClient = this.httpClient,
        taskRecorder = this.taskRecorder,
        isCancelled = false,
        bookDatabaseEntry = this.bookDatabaseEntry,
        bookInitial = bookInitial,
        contentResolver = this.contentResolver
      )

    this.context.services = this.services
  }

  @AfterEach
  fun tearDown() {
    this.webServer.close()
  }

  @Test
  fun testUpdateCredentialsBorrowACSM() {
    val validACSM = """<fulfillmentToken
      xmlns="http://ns.adobe.com/adept"
      xmlns:f="http://purl.org/dc/elements/1.1/">
      <resourceItemInfo></resourceItemInfo>
      <metadata></metadata>
      <f:format>application/epub+zip</f:format>
      </fulfillmentToken>
    """.trimIndent()

    val temporaryFile =
      TestDirectories.temporaryFileOf("book.epub", "A cold star looked down on his creations")
    val adobeLoanID =
      AdobeLoanID("4cca8916-d0fe-44ed-85d9-a8212764375d")

    val adobeNetProvider =
      MockAdobeAdeptNetProvider()
    val adobeResourceProvider =
      MockAdobeAdeptResourceProvider()
    val adobeConnector =
      MockAdobeAdeptConnector(adobeNetProvider, adobeResourceProvider)
    val adobeExecutorService =
      Executors.newSingleThreadExecutor()
    val adobeExecutor =
      MockAdobeAdeptExecutor(adobeExecutorService, adobeConnector)

    adobeConnector.onFulfill = { listener, acsm, user ->
      listener.onFulfillmentSuccess(
        temporaryFile,
        AdobeAdeptLoan(
          adobeLoanID,
          "You're a blank. You don't have rights.".toByteArray(),
          false
        )
      )
    }

    val bookDatabaseEPUBHandle =
      MockBookDatabaseEntryFormatHandleEPUB(this.bookID)
    this.bookDatabaseEntry.formatHandlesField.clear()
    this.bookDatabaseEntry.formatHandlesField.add(bookDatabaseEPUBHandle)
    bookDatabaseEPUBHandle.drmInformationHandleField = MockDRMInformationACSHandle()

    this.context.adobeExecutor = adobeExecutor
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(StandardFormatNames.adobeACSMFiles, null, emptyMap())
    this.context.opdsAcquisitionPath =
      OPDSAcquisitionPath(
        OPDSAcquisition(
          OPDSAcquisition.Relation.ACQUISITION_GENERIC,
          this.webServer.url("/book.acsm").toUri(),
          StandardFormatNames.adobeACSMFiles,
          listOf(),
          emptyMap()
        ),
        listOf(
          OPDSAcquisitionPathElement(
            StandardFormatNames.adobeACSMFiles,
            this.webServer.url("/book.acsm").toUri(),
            emptyMap()
          ),
          OPDSAcquisitionPathElement(
            StandardFormatNames.genericEPUBFiles,
            this.webServer.url("/book.epub").toUri(),
            emptyMap()
          )
        )
      )

    val validACSMResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("content-type", StandardFormatNames.adobeACSMFiles.fullType)
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setBody(validACSM)

    val task = BorrowACSM.createSubtask()

    this.context.currentURIField =
      this.webServer.url("/book.acsm").toUri()

    this.webServer.enqueue(validACSMResponse)

    try {
      task.execute(this.context)
      Assertions.fail()
    } catch (e: BorrowSubtaskException.BorrowSubtaskHaltedEarly) {
      this.logger.debug("correctly halted early: ", e)
    }

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }

  @Test
  fun testUpdateCredentialsBorrowAxisNow() {
    val axisNowService = MockAxisNowService()
    axisNowService.onFulfill = { token, tempFactory ->
      val fakeBook = context.temporaryFile().apply { createNewFile() }
      val fakeLicense = context.temporaryFile().apply { createNewFile() }
      val fakeUserKey = context.temporaryFile().apply { createNewFile() }
      AxisNowFulfillment(fakeBook, fakeLicense, fakeUserKey)
    }

    val bookDatabaseEPUBHandle =
      MockBookDatabaseEntryFormatHandleEPUB(this.bookID)
    this.bookDatabaseEntry.formatHandlesField.clear()
    this.bookDatabaseEntry.formatHandlesField.add(bookDatabaseEPUBHandle)
    bookDatabaseEPUBHandle.drmInformationHandleField = MockDRMInformationAxisHandle()

    this.context.axisNowService = axisNowService
    executeTask(task = BorrowAxisNow.createSubtask())
  }

  @Test
  fun testUpdateCredentialsBorrowDirectDownload() {
    executeTask(task = BorrowDirectDownload.createSubtask())
  }

  @Test
  fun testUpdateCredentialsBorrowLCP() {
    val tempDirPath = this.tempDir!!.toPath()

    bookDatabaseEntry.formatHandlesField.add(
      MockBookDatabaseEntryFormatHandleEPUB(
        bookID,
        Files.createTempDirectory(tempDirPath, "lcpFormatHandleEPUB").toFile()
      ).apply {
        drmInformationHandleField = BookDRMInformationHandleLCP(
          directory = Files.createTempDirectory(tempDirPath, "lcpDRMInfoEPUB").toFile(),
          format = formatDefinition,
          onUpdate = {}
        )
      }
    )
    context.lcpService = MockLCPService(
      publication = null
    )
    this.account.setAccountProvider(
      MockAccountProviders.fakeProvider(
        "urn:uuid:ea9480d4-5479-4ef1-b1d1-84ccbedb680f",
        webServer.hostName,
        webServer.port
      )
    )

    val feedEntry = BorrowTestFeeds.opdsLCPFeedEntryOfType(
      webServer = this.webServer,
      mime = "application/epub+zip",
      id = "urn:isbn:123456789",
      hashedPassphrase = ""
    )

    val licenseTargetPath = "/library/works/38859/fulfill/13"

    val acquisitionPath = OPDSAcquisitionPath(
      source = Mockito.mock(OPDSAcquisition::class.java),
      elements = listOf(
        OPDSAcquisitionPathElement(
          mimeType = StandardFormatNames.lcpLicenseFiles,
          target = webServer.url(licenseTargetPath).toUri(),
          properties = mapOf()
        ),
        OPDSAcquisitionPathElement(
          mimeType = StandardFormatNames.genericEPUBFiles,
          target = null,
          properties = mapOf()
        )
      )
    )

    val book = Book(
      id = BookIDs.newFromOPDSEntry(feedEntry),
      account = accountID,
      cover = null,
      thumbnail = null,
      entry = feedEntry,
      formats = listOf()
    )

    val databaseEntry = MockBookDatabaseEntry(book).apply {
      formatHandlesField.clear()
      formatHandlesField.add(
        MockBookDatabaseEntryFormatHandleEPUB(
          book.id,
          Files.createTempDirectory(tempDirPath, "lcpFormatHandleEPUB").toFile()
        ).apply {
          drmInformationHandleField = BookDRMInformationHandleLCP(
            directory = Files.createTempDirectory(tempDirPath, "lcpDRMInfoEPUB").toFile(),
            format = formatDefinition,
            onUpdate = {}
          )
        }
      )
    }

    context.bookDatabaseEntry = databaseEntry
    context.opdsAcquisitionPath = acquisitionPath
    context.currentAcquisitionPathElement = acquisitionPath.elements.first()

    // Expect a request for the loans feed to obtain the hashed passphrase for the book.
    val newHashedPassphrase = "b9e3323fb715306bd89fa311b4bc988cce0042fb1d9079d13f41acc10c6ef37a"

    val loansFeedResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/atom+xml;profile=opds-catalog;kind=acquisition")
        .setBody(
          """
          <feed xmlns="http://www.w3.org/2005/Atom" xmlns:schema="http://schema.org/" xmlns:lcp="http://readium.org/lcp-specs/ns">
            <id>https://example.com/library/loans/</id>
            <title>Loans</title>
            <updated>2022-03-10T00:53:24+00:00</updated>
            <entry schema:additionalType="http://schema.org/EBook">
              <title>Example EPUB</title>
              <id>${feedEntry.id}</id>
              <updated>2022-03-07T22:07:34+00:00</updated>
              <link type="application/vnd.readium.lcp.license.v1.0+json" rel="http://opds-spec.org/acquisition">
                <lcp:hashed_passphrase>$newHashedPassphrase</lcp:hashed_passphrase>
              </link>
            </entry>
          </feed>
          """
        )

    this.webServer.enqueue(loansFeedResponse)

    // Expect a request to the acquisition URL to get the LCP license.

    val licensePublicationPath = "/publication/12345"
    val licensePublicationURL = webServer.url(licensePublicationPath)

    val licenseResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/vnd.readium.lcp.license.v1.0+json")
        .setBody(
          """
          {
            "provider": "https://www.cantook.net",
            "id": "9876",
            "issued": "2022-02-22T19:37:31Z",
            "updated": "2022-02-22T19:37:31Z",
            "encryption": {
              "profile": "http://readium.org/lcp/profile-1.0",
              "content_key": {
                "algorithm": "http://www.w3.org/2001/04/xmlenc#aes256-cbc",
                "encrypted_value": "foo"
              },
              "user_key": {
                "algorithm": "http://www.w3.org/2001/04/xmlenc#sha256",
                "text_hint": "Please contact your administrator to restore the passphrase",
                "key_check": "foo"
              }
            },
            "links": [
              {
                "rel": "hint",
                "href": "https://example.org/lcp/hint",
                "type": "text/html"
              },
              {
                "rel": "status",
                "href": "https://example.org/status",
                "type": "application/vnd.readium.license.status.v1.0+json"
              },
              {
                "rel": "publication",
                "href": "$licensePublicationURL",
                "type": "application/epub+zip"
              }
            ],
            "user": {
              "id": "1234"
            },
            "rights": {
              "print": 0,
              "copy": 0,
              "start": "2022-02-22T19:37:31Z",
              "end": "2022-03-15T19:36:56Z"
            },
            "signature": {
              "certificate": "foo",
              "value": "bar",
              "algorithm": "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"
            }
          }
          """
        )

    this.webServer.enqueue(licenseResponse)

    // Expect a request to download the publication.

    val downloadResponse =
      MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/epub+zip")
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setBody(
          Buffer().readFrom(
            BorrowBookRefreshTokenTest::class.java.getResourceAsStream(
              "/org/nypl/simplified/tests/books/minimal.epub"
            ) ?: ByteArrayInputStream(byteArrayOf())
          )
        )

    this.webServer.enqueue(downloadResponse)

    try {
      BorrowLCP.createSubtask().execute(context)
      Assertions.fail()
    } catch (e: BorrowSubtaskException.BorrowSubtaskHaltedEarly) {
      this.logger.debug("correctly halted early: ", e)
    }

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }

  @Test
  fun testUpdateCredentialsBorrowLoanCreate() {
    this.context.currentURIField =
      this.webServer.url("/book.epub").toUri()
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(StandardFormatNames.opdsAcquisitionFeedEntry, null, emptyMap())
    this.context.currentRemainingOPDSPathElements =
      listOf(OPDSAcquisitionPathElement(StandardFormatNames.genericEPUBFiles, null, emptyMap()))

    val feedText = """
<entry xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
  <title>Example</title>
  <updated>2020-09-17T16:48:51+0000</updated>
  <id>7264f7f8-7bea-4ce6-906e-615406ca38cb</id>
  <link href="${this.webServer.url("/next")}" rel="http://opds-spec.org/acquisition" type="application/epub+zip">
    <opds:availability since="2020-09-17T16:48:51+0000" status="available" until="2020-09-17T16:48:51+0000" />
    <opds:holds total="0" />
    <opds:copies available="5" total="5" />
  </link>
</entry>
    """.trimIndent()

    val response =
      MockResponse()
        .setResponseCode(200)
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setHeader("Content-Type", StandardFormatNames.opdsAcquisitionFeedEntry)
        .setBody(feedText)

    this.webServer.enqueue(response)

    BorrowLoanCreate.createSubtask().execute(this.context)

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }

  private fun executeTask(task: BorrowSubtaskType) {
    this.context.currentURIField =
      this.webServer.url("/book.epub").toUri()
    this.context.currentAcquisitionPathElement =
      OPDSAcquisitionPathElement(StandardFormatNames.genericEPUBFiles, null, emptyMap())

    val response0 =
      MockResponse()
        .setResponseCode(200)
        .setHeader(LSHTTPRequestConstants.PROPERTY_KEY_ACCESS_TOKEN, "ghij")
        .setHeader("Content-Type", "application/epub+zip")
        .setBody("EPUB!")

    this.webServer.enqueue(response0)

    task.execute(this.context)

    Assertions.assertEquals(
      "ghij",
      (account.loginState.credentials as AccountAuthenticationCredentials.BasicToken)
        .authenticationTokenInfo.accessToken
    )
  }
}
