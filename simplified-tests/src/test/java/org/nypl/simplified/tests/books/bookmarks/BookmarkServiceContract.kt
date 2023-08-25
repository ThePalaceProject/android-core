package org.nypl.simplified.tests.books.bookmarks

import android.content.Context
import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.subjects.Subject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountLoginState.AccountLoggedIn
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountPreferences
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookLocation
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryType
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfileType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.bookmarks.api.BookmarkSyncEnableResult.SYNC_ENABLE_NOT_SUPPORTED
import org.nypl.simplified.bookmarks.internal.BHTTPCalls
import org.nypl.simplified.tests.EventAssertions
import org.nypl.simplified.tests.EventLogging
import org.nypl.simplified.tests.mocking.MockProfilesController
import org.slf4j.Logger
import java.net.InetAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

abstract class BookmarkServiceContract {

  val fakeAccountID =
    AccountID(UUID.fromString("46d17029-14ba-4e34-bcaa-def02713575a"))

  protected abstract val logger: Logger

  protected abstract fun bookmarkService(
    threads: (Runnable) -> Thread,
    events: Subject<BookmarkEvent>,
    httpCalls: BookmarkHTTPCallsType,
    profilesController: ProfilesControllerType
  ): BookmarkServiceType

  private val objectMapper = ObjectMapper()
  private var readerBookmarkService: BookmarkServiceType? = null
  private lateinit var server: MockWebServer
  private lateinit var serverDispatcher: EndpointDispatcher
  private lateinit var http: LSHTTPClientType
  private lateinit var annotationsURI: URI
  private lateinit var patronURI: URI

  private val annotationsEmpty = """
{
   "id" : "http://www.example.com/annotations/",
   "type" : [
      "BasicContainer",
      "AnnotationCollection"
   ],
   "@context" : [
      "http://www.w3.org/ns/anno.jsonld",
      "http://www.w3.org/ns/ldp.jsonld"
   ],
   "total" : 0,
   "first" : {
      "items" : [],
      "type" : "AnnotationPage",
      "id" : "http://www.example.com/annotations/"
   }
}
"""

  private val patronSettingsWithAnnotationsEnabled = """
{
  "settings": {
    "simplified:synchronize_annotations": true
  }
}
"""

  private val patronSettingsWithAnnotationsDisabled = """
{
  "settings": {
    "simplified:synchronize_annotations": false
  }
}
"""

  private val accountCredentials =
    AccountAuthenticationCredentials.Basic(
      userName = AccountUsername("abcd"),
      password = AccountPassword("1234"),
      adobeCredentials = null,
      authenticationDescription = null,
      annotationsURI = null
    )

  private fun addResponse(
    uri: String,
    response: String
  ) {
    this.serverDispatcher.addResponse(
      endpoint = uri,
      response = MockResponse()
        .setResponseCode(200)
        .setBody(response)
    )
  }

  @BeforeEach
  fun setup() {
    /*
     * MockWebServer doesn't handle IPv6 for some reason, but the tests executing on a local
     * machine will try to access the server over IPv6 first on some systems.
     */

    System.setProperty("java.net.preferIPv4Stack", "true")

    this.http =
      LSHTTPClients()
        .create(
          context = Mockito.mock(Context::class.java),
          configuration = LSHTTPClientConfiguration(
            applicationName = "simplified-test",
            applicationVersion = "0.0.1",
            tlsOverrides = null,
            timeout = Pair(5L, TimeUnit.SECONDS)
          )
        )

    this.server = MockWebServer()
    this.server.start(
      InetAddress.getByName("127.0.0.1"), 10000
    )
    this.serverDispatcher = EndpointDispatcher()
    this.server.dispatcher = this.serverDispatcher
    this.annotationsURI =
      URI.create("http://localhost:10000/annotations")
    this.patronURI =
      URI.create("http://localhost:10000/patron")
  }

  @AfterEach
  fun tearDown() {
    this.readerBookmarkService?.close()
    this.server.shutdown()
    this.server.close()
  }

  /**
   * Initializing the bookmarks controller with a single account that permits and supports syncing
   * but has no books, succeeds quietly.
   */

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testInitializeEmpty() {
    this.addResponse("/patron", this.patronSettingsWithAnnotationsEnabled)
    this.addResponse("/annotations", this.annotationsEmpty)

    val httpCalls = BHTTPCalls(this.objectMapper, this.http)

    val profileEvents =
      EventLogging.create<ProfileEvent>(this.logger, 1)
    val bookmarkEvents =
      EventLogging.create<BookmarkEvent>(this.logger, 2)
    val accountEvents =
      EventLogging.create<AccountEvent>(this.logger, 1)

    val books =
      Mockito.mock(BookDatabaseType::class.java)

    Mockito.`when`(books.books())
      .thenReturn(sortedSetOf())

    val accountProvider =
      Mockito.mock(AccountProviderType::class.java)

    Mockito.`when`(accountProvider.patronSettingsURI)
      .thenReturn(this.patronURI)

    val accountPreferences =
      AccountPreferences(
        bookmarkSyncingPermitted = true,
        catalogURIOverride = null,
        announcementsAcknowledged = listOf()
      )

    val account =
      Mockito.mock(AccountType::class.java)

    Mockito.`when`(account.loginState)
      .thenReturn(
        AccountLoggedIn(
          this.accountCredentials.copy(annotationsURI = this.annotationsURI)
        )
      )
    Mockito.`when`(account.id)
      .thenReturn(this.fakeAccountID)
    Mockito.`when`(account.provider)
      .thenReturn(accountProvider)
    Mockito.`when`(account.bookDatabase)
      .thenReturn(books)
    Mockito.`when`(account.preferences)
      .thenReturn(accountPreferences)

    val profile =
      Mockito.mock(ProfileType::class.java)

    Mockito.`when`(profile.account(this.fakeAccountID))
      .thenReturn(account)
    Mockito.`when`(profile.accounts())
      .thenReturn(sortedMapOf(Pair(this.fakeAccountID, account)))
    Mockito.`when`(profile.id)
      .thenReturn(ProfileID.generate())

    val profiles =
      Mockito.mock(ProfilesControllerType::class.java)

    Mockito.`when`(profiles.profileEvents())
      .thenReturn(profileEvents.events)
    Mockito.`when`(profiles.accountEvents())
      .thenReturn(accountEvents.events)
    Mockito.`when`(profiles.profileCurrent())
      .thenReturn(profile)

    this.readerBookmarkService =
      this.bookmarkService(::Thread, bookmarkEvents.events, httpCalls, profiles)

    bookmarkEvents.latch.await()

    EventAssertions.isTypeAndMatches(
      BookmarkEvent.BookmarkSyncStarted::class.java,
      bookmarkEvents.eventLog,
      0,
      { event -> Assertions.assertEquals(this.fakeAccountID, event.accountID) }
    )

    EventAssertions.isTypeAndMatches(
      BookmarkEvent.BookmarkSyncFinished::class.java,
      bookmarkEvents.eventLog,
      1,
      { event -> Assertions.assertEquals(this.fakeAccountID, event.accountID) }
    )

    val allRequests = this.takeAllRequests()
    Assertions.assertTrue(
      allRequests.any { request ->
        request.requestUrl?.toUri() == this.patronURI
      },
      "At least one request made to ${this.patronURI}"
    )
    Assertions.assertTrue(
      allRequests.any { request ->
        request.requestUrl?.toUri() == this.annotationsURI
      },
      "At least one request made to ${this.annotationsURI}"
    )
    Assertions.assertEquals(2, allRequests.size)
  }

  private fun takeAllRequests(): List<RecordedRequest> {
    val requests = mutableListOf<RecordedRequest>()
    for (i in 0 until this.server.requestCount) {
      val request = this.server.takeRequest()
      this.logger.debug("requests [$i]: {}", request.requestUrl?.toUri())
      requests.add(request)
    }
    return requests.toList()
  }

  /**
   * Initializing the bookmarks controller with a single account that permits and supports syncing
   * but has no books, succeeds quietly.
   */

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testInitializeReceive() {
    this.addResponse("/patron", this.patronSettingsWithAnnotationsEnabled)

    this.addResponse(
      "/annotations",
      """
    {
       "id" : "http://www.example.com/annotations/",
       "type" : [
          "BasicContainer",
          "AnnotationCollection"
       ],
       "@context" : [
          "http://www.w3.org/ns/anno.jsonld",
          "http://www.w3.org/ns/ldp.jsonld"
       ],
       "total" : 0,
       "first" : {
          "items" : [
             {
                "body" : {
                   "http://librarysimplified.org/terms/device" : "urn:uuid:253c7cbc-4fdf-430e-81b9-18bea90b6026",
                   "http://librarysimplified.org/terms/time" : "2018-12-03T16:29:03"
                },
                "id" : "http://www.example.com/annotations/100000",
                "type" : "Annotation",
                "motivation" : "http://www.w3.org/ns/oa#bookmarking",
                "target" : {
                   "selector" : {
                      "value" : "{\"idref\":\"n-1\",\"contentCFI\":\"/4/14,/1:0,/1:1\"}",
                      "type" : "FragmentSelector"
                   },
                   "source" : "urn:example.com/terms/id/c083c0a6-54c6-4cc5-9d3a-425317da662a"
                }
             }
          ],
          "type" : "AnnotationPage",
          "id" : "http://www.example.com/annotations/"
       }
    }
    """
    )

    val httpCalls = BHTTPCalls(this.objectMapper, this.http)

    val profileEvents =
      EventLogging.create<ProfileEvent>(this.logger, 1)
    val bookmarkEvents =
      EventLogging.create<BookmarkEvent>(this.logger, 3)
    val accountEvents =
      EventLogging.create<AccountEvent>(this.logger, 1)

    val bookID =
      BookID.create("fab6e4ebeb3240676b3f7585f8ee4faecccbe1f9243a652153f3071e90599325")

    val receivedBookmarks =
      mutableListOf<Bookmark>()

    val format =
      BookFormat.BookFormatEPUB(
        drmInformation = BookDRMInformation.None,
        file = null,
        lastReadLocation = null,
        bookmarks = listOf(),
        contentType = BookFormats.epubMimeTypes().first()
      )

    val formatHandle =
      Mockito.mock(BookDatabaseEntryFormatHandleEPUB::class.java)

    Mockito.`when`(formatHandle.format)
      .thenReturn(format)

    Mockito.`when`(formatHandle.setBookmarks(Mockito.anyList()))
      .then { input ->
        val bookmarks: List<Bookmark> = input.arguments[0] as List<Bookmark>
        receivedBookmarks.addAll(bookmarks)
        Unit
      }

    val bookEntry =
      Mockito.mock(BookDatabaseEntryType::class.java)

    Mockito.`when`(bookEntry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java))
      .thenReturn(formatHandle)

    val books =
      Mockito.mock(BookDatabaseType::class.java)

    Mockito.`when`(books.books())
      .thenReturn(sortedSetOf())
    Mockito.`when`(books.entry(bookID))
      .thenReturn(bookEntry)

    val accountProvider =
      Mockito.mock(AccountProviderType::class.java)

    Mockito.`when`(accountProvider.patronSettingsURI)
      .thenReturn(this.patronURI)

    val accountPreferences =
      AccountPreferences(
        bookmarkSyncingPermitted = true,
        catalogURIOverride = null,
        announcementsAcknowledged = listOf()
      )

    val account =
      Mockito.mock(AccountType::class.java)

    Mockito.`when`(account.loginState)
      .thenReturn(
        AccountLoggedIn(
          this.accountCredentials.copy(annotationsURI = this.annotationsURI)
        )
      )
    Mockito.`when`(account.id)
      .thenReturn(this.fakeAccountID)
    Mockito.`when`(account.provider)
      .thenReturn(accountProvider)
    Mockito.`when`(account.bookDatabase)
      .thenReturn(books)
    Mockito.`when`(account.preferences)
      .thenReturn(accountPreferences)

    val profile =
      Mockito.mock(ProfileType::class.java)

    Mockito.`when`(profile.accounts())
      .thenReturn(sortedMapOf(Pair(this.fakeAccountID, account)))
    Mockito.`when`(profile.id)
      .thenReturn(ProfileID.generate())
    Mockito.`when`(profile.account(this.fakeAccountID))
      .thenReturn(account)

    val profiles =
      Mockito.mock(ProfilesControllerType::class.java)

    Mockito.`when`(profiles.profileEvents())
      .thenReturn(profileEvents.events)
    Mockito.`when`(profiles.accountEvents())
      .thenReturn(accountEvents.events)
    Mockito.`when`(profiles.profileCurrent())
      .thenReturn(profile)

    this.readerBookmarkService =
      this.bookmarkService(::Thread, bookmarkEvents.events, httpCalls, profiles)

    bookmarkEvents.latch.await()

    EventAssertions.isTypeAndMatches(
      BookmarkEvent.BookmarkSyncStarted::class.java,
      bookmarkEvents.eventLog,
      0,
      { event -> Assertions.assertEquals(this.fakeAccountID, event.accountID) }
    )

    EventAssertions.isTypeAndMatches(
      BookmarkEvent.BookmarkSaved::class.java,
      bookmarkEvents.eventLog,
      1,
      { event -> Assertions.assertEquals(this.fakeAccountID, event.accountID) }
    )

    EventAssertions.isTypeAndMatches(
      BookmarkEvent.BookmarkSyncFinished::class.java,
      bookmarkEvents.eventLog,
      2,
      { event -> Assertions.assertEquals(this.fakeAccountID, event.accountID) }
    )

    Assertions.assertEquals(1, receivedBookmarks.size)
    Assertions.assertEquals("urn:example.com/terms/id/c083c0a6-54c6-4cc5-9d3a-425317da662a", receivedBookmarks[0].opdsId)

    val allRequests = this.takeAllRequests()
    Assertions.assertTrue(
      allRequests.any { request ->
        request.requestUrl?.toUri() == this.patronURI
      },
      "At least one request made to ${this.patronURI}"
    )
    Assertions.assertTrue(
      allRequests.any { request ->
        request.requestUrl?.toUri() == this.annotationsURI
      },
      "At least one request made to ${this.annotationsURI}"
    )
    Assertions.assertEquals(2, allRequests.size)
  }

  /**
   * Initializing the bookmarks controller with a single account that permits and supports syncing
   * and has bookmarks, succeeds quietly.
   */

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  fun testInitializeSendBookmarks() {
    this.addResponse("/patron", this.patronSettingsWithAnnotationsEnabled)

    val responseText = """
    {
       "id" : "http://www.example.com/annotations/",
       "type" : [
          "BasicContainer",
          "AnnotationCollection"
       ],
       "@context" : [
          "http://www.w3.org/ns/anno.jsonld",
          "http://www.w3.org/ns/ldp.jsonld"
       ],
       "total" : 0,
       "first" : {
          "items" : [
             {
                "body" : {
                   "http://librarysimplified.org/terms/device" : "urn:uuid:253c7cbc-4fdf-430e-81b9-18bea90b6026",
                   "http://librarysimplified.org/terms/time" : "2018-12-03T16:29:03"
                },
                "id" : "http://www.example.com/annotations/100000",
                "type" : "Annotation",
                "motivation" : "http://www.w3.org/ns/oa#bookmarking",
                "target" : {
                   "selector" : {
                      "value" : "{\"idref\":\"n-1\",\"contentCFI\":\"/4/14,/1:0,/1:1\"}",
                      "type" : "FragmentSelector"
                   },
                   "source" : "urn:example.com/terms/id/c083c0a6-54c6-4cc5-9d3a-425317da662a"
                }
             }
          ],
          "type" : "AnnotationPage",
          "id" : "http://www.example.com/annotations/"
       }
    }
    """

    this.addResponse("/annotations", responseText)

    val httpCalls = BHTTPCalls(this.objectMapper, this.http)

    val profileEvents =
      EventLogging.create<ProfileEvent>(this.logger, 1)
    val bookmarkEvents =
      EventLogging.create<BookmarkEvent>(this.logger, 3)
    val accountEvents =
      EventLogging.create<AccountEvent>(this.logger, 1)

    val bookID =
      BookID.create("fab6e4ebeb3240676b3f7585f8ee4faecccbe1f9243a652153f3071e90599325")

    val receivedBookmarks =
      mutableListOf<Bookmark>()

    val startingBookmarks =
      listOf(
        Bookmark.ReaderBookmark.create(
          opdsId = "urn:example.com/terms/id/c083c0a6-54c6-4cc5-9d3a-425317da662a",
          location = BookLocation.BookLocationR1(0.5, null, "x"),
          kind = BookmarkKind.BookmarkLastReadLocation,
          time = DateTime.now(DateTimeZone.UTC),
          chapterTitle = "A Title",
          bookProgress = 0.5,
          deviceID = "urn:uuid:253c7cbc-4fdf-430e-81b9-18bea90b6026",
          uri = null
        )
      )

    val format =
      BookFormat.BookFormatEPUB(
        drmInformation = BookDRMInformation.None,
        file = null,
        lastReadLocation = null,
        bookmarks = startingBookmarks,
        contentType = BookFormats.epubMimeTypes().first()
      )

    val formatHandle =
      Mockito.mock(BookDatabaseEntryFormatHandleEPUB::class.java)

    Mockito.`when`(formatHandle.format)
      .thenReturn(format)

    Mockito.`when`(formatHandle.setBookmarks(Mockito.anyList()))
      .then { input ->
        val bookmarks: List<Bookmark> = input.arguments[0] as List<Bookmark>
        receivedBookmarks.addAll(bookmarks)
        Unit
      }

    val bookEntry =
      Mockito.mock(BookDatabaseEntryType::class.java)

    Mockito.`when`(bookEntry.findFormatHandle(BookDatabaseEntryFormatHandleEPUB::class.java))
      .thenReturn(formatHandle)

    val books =
      Mockito.mock(BookDatabaseType::class.java)

    Mockito.`when`(books.books())
      .thenReturn(sortedSetOf())
    Mockito.`when`(books.entry(bookID))
      .thenReturn(bookEntry)

    val accountProvider =
      Mockito.mock(AccountProviderType::class.java)

    Mockito.`when`(accountProvider.patronSettingsURI)
      .thenReturn(this.patronURI)

    val accountPreferences =
      AccountPreferences(
        bookmarkSyncingPermitted = true,
        catalogURIOverride = null,
        announcementsAcknowledged = listOf()
      )

    val account =
      Mockito.mock(AccountType::class.java)

    Mockito.`when`(account.loginState)
      .thenReturn(
        AccountLoggedIn(
          this.accountCredentials.copy(annotationsURI = this.annotationsURI)
        )
      )
    Mockito.`when`(account.id)
      .thenReturn(this.fakeAccountID)
    Mockito.`when`(account.provider)
      .thenReturn(accountProvider)
    Mockito.`when`(account.bookDatabase)
      .thenReturn(books)
    Mockito.`when`(account.preferences)
      .thenReturn(accountPreferences)

    val profile =
      Mockito.mock(ProfileType::class.java)

    Mockito.`when`(profile.accounts())
      .thenReturn(sortedMapOf(Pair(this.fakeAccountID, account)))
    Mockito.`when`(profile.id)
      .thenReturn(ProfileID.generate())
    Mockito.`when`(profile.account(this.fakeAccountID))
      .thenReturn(account)

    val profiles =
      Mockito.mock(ProfilesControllerType::class.java)

    Mockito.`when`(profiles.profileEvents())
      .thenReturn(profileEvents.events)
    Mockito.`when`(profiles.accountEvents())
      .thenReturn(accountEvents.events)
    Mockito.`when`(profiles.profileCurrent())
      .thenReturn(profile)

    this.readerBookmarkService =
      this.bookmarkService(::Thread, bookmarkEvents.events, httpCalls, profiles)

    this.waitForServiceQuiescence(
      this.readerBookmarkService!!,
      MockProfilesController(1, 1)
    )

    EventAssertions.isTypeAndMatches(
      BookmarkEvent.BookmarkSyncStarted::class.java,
      bookmarkEvents.eventLog,
      0,
      { event -> Assertions.assertEquals(this.fakeAccountID, event.accountID) }
    )

    EventAssertions.isTypeAndMatches(
      BookmarkEvent.BookmarkSaved::class.java,
      bookmarkEvents.eventLog,
      1,
      { event -> Assertions.assertEquals(this.fakeAccountID, event.accountID) }
    )

    EventAssertions.isTypeAndMatches(
      BookmarkEvent.BookmarkSyncFinished::class.java,
      bookmarkEvents.eventLog,
      2,
      { event -> Assertions.assertEquals(this.fakeAccountID, event.accountID) }
    )

    Assertions.assertEquals(2, receivedBookmarks.size)
    Assertions.assertEquals("urn:example.com/terms/id/c083c0a6-54c6-4cc5-9d3a-425317da662a", receivedBookmarks[0].opdsId)
    Assertions.assertEquals("urn:example.com/terms/id/c083c0a6-54c6-4cc5-9d3a-425317da662a", receivedBookmarks[1].opdsId)

    val allRequests = this.takeAllRequests()
    Assertions.assertTrue(
      allRequests.any { request ->
        this.matchesEndpoint(request, "/patron")
      },
      "At least one request made to ${this.patronURI}"
    )
    Assertions.assertTrue(
      allRequests.any { request ->
        this.matchesEndpoint(request, "/annotations")
      },
      "At least one request made to ${this.annotationsURI}"
    )
    Assertions.assertEquals(2, allRequests.size)
  }

  /**
   * Trying to enable syncing on an account that doesn't support it, returns an appropriate status
   * code.
   */

  @Test
  @Timeout(value = 10L, unit = TimeUnit.SECONDS)
  fun testEnableBookmarkSyncingNotSupported() {
    val httpCalls =
      BHTTPCalls(this.objectMapper, this.http)
    val bookmarkEvents =
      EventLogging.create<BookmarkEvent>(this.logger, 3)
    val profiles =
      MockProfilesController(1, 1)

    this.readerBookmarkService =
      this.bookmarkService(
        threads = ::Thread,
        events = bookmarkEvents.events,
        httpCalls = httpCalls,
        profilesController = profiles
      )

    val service =
      this.readerBookmarkService!!
    val result =
      service.bookmarkSyncEnable(profiles.profileList[0].accountList[0].id, true).get()

    this.waitForServiceQuiescence(service, profiles)
    Assertions.assertEquals(SYNC_ENABLE_NOT_SUPPORTED, result)
  }

  /*
   * Wait for the bookmark service to finish all of the requests.
   */

  private fun waitForServiceQuiescence(
    service: BookmarkServiceType,
    profiles: MockProfilesController
  ) {
    Thread.sleep(1_000L)

    try {
      service.bookmarkLoad(
        accountID = profiles.profileList[0].accountList[0].id,
        book = BookID.create("x"),
        lastReadBookmarkServer = null
      ).get(3L, TimeUnit.SECONDS)
    } catch (e: Exception) {
      // Not a problem
    }
  }

  private fun matchesEndpoint(
    request: RecordedRequest,
    endpoint: String
  ): Boolean {
    val requestUri = request.requestUrl?.toUri()
    return requestUri?.path == endpoint
  }
}
