package org.nypl.simplified.feeds

import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addBasicTokenPropertiesIfApplicable
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedAcquisitionPath
import org.nypl.simplified.feeds.api.FeedAcquisitionPaths
import org.nypl.simplified.feeds.api.FeedAcquisitionRelation
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.feeds.api.FeedDataWithGroups
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedFetchResult
import org.nypl.simplified.feeds.api.FeedFetchType
import org.nypl.simplified.feeds.api.FeedFetchVersion
import org.nypl.simplified.feeds.api.FeedHTTPTransportException
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderSuccess
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.feeds.opds12.OPDS12FeedConverter
import org.nypl.simplified.feeds.opds20.OPDS20FeedConverter
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.opds.core.OPDSOpenSearch1_1
import org.nypl.simplified.opds.core.OPDSSearchLink
import org.nypl.simplified.opds.core.OPDSSearchParser
import org.nypl.simplified.opds.core.OPDSSearchParserType
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.SortedMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A unified feed loader that handles both OPDS 1.2 and OPDS 2.0 serializations.
 *
 * The [preferOPDS20] flag controls whether the loader sends an
 * `Accept: application/opds+json` header to the server. When `true`, the
 * loader requests OPDS 2.0 and parses the response as OPDS 2.0 JSON using
 * the Palace WebPub parser. If the server responds with a non-OPDS-2.0 content
 * type, the loader falls back to the OPDS 1.2 parser. When `false` (the
 * default), the loader does not send the header and the server responds
 * with OPDS 1.2.
 */
class FeedLoader(
  private val bookFormatSupport: BookFormatSupportType,
  private val exec: ExecutorService,
  private val fetch: FeedFetchType,
) : FeedLoaderType {
  private val opds12Parser: OPDSFeedParserType =
    OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser())
  private val opds12SearchParser: OPDSSearchParserType =
    OPDSSearchParser.newParser()
  private val log = LoggerFactory.getLogger(FeedLoader::class.java)

  private val showOnlySupportedBooksFlag = AtomicBoolean(true)
  private val preferOPDS20Flag = AtomicBoolean(false)

  override var showOnlySupportedBooks: Boolean
    get() = this.showOnlySupportedBooksFlag.get()
    set(value) {
      this.showOnlySupportedBooksFlag.set(value)
    }

  override var preferOPDS20: Boolean
    get() = this.preferOPDS20Flag.get()
    set(value) {
      this.preferOPDS20Flag.set(value)
    }

  override fun fetchURI(
    accountID: AccountID,
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    method: String,
  ): CompletableFuture<FeedLoaderResult> {
    val future = CompletableFuture<FeedLoaderResult>()
    this.exec.execute {
      try {
        future.complete(
          this.fetchSynchronously(
            accountId = accountID,
            uri = uri,
            credentials = credentials,
            method = method,
          ),
        )
      } catch (e: Throwable) {
        future.completeExceptionally(e)
      }
    }
    return future
  }

  private fun fetchSynchronously(
    accountId: AccountID,
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    method: String,
  ): FeedLoaderResult {
    try {
      val version: FeedFetchVersion
      if (this.preferOPDS20) {
        version = FeedFetchVersion.OPDS_2_0
      } else {
        version = FeedFetchVersion.OPDS_1_2
      }
      val result = this.fetch.fetch(uri, credentials, version)
      val body = result.body
      val accessToken = result.accessToken
      val contentType: MIMEType? = result.contentType

      val feed: Feed =
        this.parseFeed(accountId, body, uri, contentType, credentials, method)

      return FeedLoaderSuccess(
        feed = this.applyFilter(feed, accountId),
        accessToken = accessToken,
      )
    } catch (e: FeedHTTPTransportException) {
      this.log.debug("feed transport exception: ", e)

      if (e.code == 401) {
        return FeedLoaderFailure.FeedLoaderFailedAuthentication(
          problemReport = e.report,
          exception = e,
          attributesInitial = this.errorAttributesOf(uri, method),
          message = this.localMessageOf(e),
        )
      }
      return FeedLoaderFailure.FeedLoaderFailedGeneral(
        problemReport = e.report,
        exception = e,
        attributesInitial = this.errorAttributesOf(uri, method),
        message = this.localMessageOf(e),
      )
    } catch (e: Exception) {
      this.log.debug("feed exception: ", e)

      return FeedLoaderFailure.FeedLoaderFailedGeneral(
        problemReport = null,
        exception = e,
        attributesInitial = this.errorAttributesOf(uri, method),
        message = this.localMessageOf(e),
      )
    }
  }

  private fun parseOPDS12(
    accountId: AccountID,
    body: InputStream,
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    method: String,
  ): Feed {
    val opdsFeed = body.use { stream -> this.opds12Parser.parse(uri, stream) }
    val search = this.fetchSearchLink(opdsFeed.feedSearchURI, credentials, method)

    return OPDS12FeedConverter.convert(
      accountID = accountId,
      feed = opdsFeed,
      search = search,
    )
  }

  /**
   * Parse the fetched body as a feed, choosing the parser from the response
   * content type. An OPDS 2.0 feed and an OPDS 2.0 publication are parsed
   * with the WebPub parser; everything else (including a missing content
   * type) is parsed as an OPDS 1.2 Atom feed.
   */
  private fun parseFeed(
    accountId: AccountID,
    body: InputStream,
    uri: URI,
    contentType: MIMEType?,
    credentials: AccountAuthenticationCredentials?,
    method: String,
  ): Feed {
    if (contentType == null) {
      this.log.debug("parsing feed as OPDS 1.2: {} (content-type: null)", uri)
      return this.parseOPDS12(accountId, body, uri, credentials, method)
    }

    return when (contentType.fullType) {
      StandardFormatNames.opds20Feed.fullType -> {
        this.log.debug("parsing feed as OPDS 2.0 feed: {}", uri)
        this.parseOPDS20Feed(accountId, body, uri)
      }

      StandardFormatNames.opds20Publication.fullType -> {
        this.log.debug("parsing feed as OPDS 2.0 publication: {}", uri)
        this.parseOPDS20Publication(accountId, body, uri)
      }

      else -> {
        this.log.debug("parsing feed as OPDS 1.2: {} (content-type: {})", uri, contentType)
        this.parseOPDS12(accountId, body, uri, credentials, method)
      }
    }
  }

  private fun parseOPDS20Feed(
    accountId: AccountID,
    body: InputStream,
    uri: URI,
  ): Feed {
    val wpmFeed = OPDS20FeedConverter.parse(body)
    return OPDS20FeedConverter.convert(accountId, wpmFeed, uri)
  }

  /**
   * Parse a single OPDS 2.0 publication (as returned by a loan revoke
   * endpoint) and wrap it in a one-entry [FeedData].
   */
  private fun parseOPDS20Publication(
    accountId: AccountID,
    body: InputStream,
    uri: URI,
  ): Feed {
    val publication = OPDS20FeedConverter.parsePublication(body)
    val entry = OPDS20FeedConverter.convertEntry(accountId, publication)

    return FeedData(
      feedID = uri.toString(),
      feedSearch = null,
      feedTitle = entry.title,
      feedUpdated = entry.updated,
      feedURI = uri,
      feedTermsOfService = null,
      feedPrivacyPolicy = null,
      feedAbout = null,
      feedLicenses = null,
      feedNext = null,
      feedAuthDocument = null,
      facetsByGroup = emptyMap(),
      facetsOrder = emptyList(),
      entries = listOf(entry),
    )
  }

  /**
   * Apply the [showOnlySupportedBooks] filter to a feed, removing entries
   * that have no supported acquisition path.
   */
  private fun applyFilter(
    feed: Feed,
    accountId: AccountID,
  ): Feed {
    if (!this.showOnlySupportedBooks) {
      return feed
    }

    return when (feed) {
      is FeedData -> {
        feed.copy(
          entries = feed.entries.filter { this.isEntrySupported(it) },
        )
      }

      is FeedDataWithGroups -> {
        feed.copy(
          groups =
            feed.groups.map { group ->
              group.copy(
                groupEntries = group.groupEntries.filter { this.isEntrySupported(it) },
              )
            },
        )
      }
    }
  }

  private fun isEntrySupported(entry: FeedEntry): Boolean {
    return if (entry is FeedEntryBook) {
      val paths = FeedAcquisitionPaths.linearize(entry.acquisitions)
      paths.any { path ->
        this.isRelationSupported(path.source.relation) &&
          this.isTypePathSupported(path)
      }
    } else {
      // Non-book entries (groups, etc.) are always supported
      true
    }
  }

  private fun isRelationSupported(relation: FeedAcquisitionRelation): Boolean {
    return when (relation) {
      FeedAcquisitionRelation.ACQUISITION_BORROW -> true
      FeedAcquisitionRelation.ACQUISITION_BUY -> false
      FeedAcquisitionRelation.ACQUISITION_GENERIC -> true
      FeedAcquisitionRelation.ACQUISITION_OPEN_ACCESS -> true
      FeedAcquisitionRelation.ACQUISITION_SAMPLE -> false
      FeedAcquisitionRelation.ACQUISITION_SUBSCRIBE -> false
    }
  }

  private fun isTypePathSupported(path: FeedAcquisitionPath): Boolean {
    return this.bookFormatSupport.isSupportedPath(path.asMIMETypes())
  }

  private fun fetchSearchLink(
    searchLink: OPDSSearchLink?,
    credentials: AccountAuthenticationCredentials?,
    method: String,
  ): OPDSOpenSearch1_1? {
    return if (searchLink != null) {
      val result = this.fetch.fetch(searchLink.uri, credentials, FeedFetchVersion.OPDS_1_2)
      result.body.use { stream ->
        this.opds12SearchParser.parse(searchLink.uri, stream)
      }
    } else {
      null
    }
  }

  private fun localMessageOf(exception: Exception): String {
    val message = exception.localizedMessage
    if (message == null) {
      return ""
    }
    return message
  }

  private fun errorAttributesOf(
    uri: URI,
    method: String,
  ): SortedMap<String, String> {
    return sortedMapOf(
      Pair("Feed", uri.toString()),
      Pair("Method", method),
    )
  }

  companion object {
    /**
     * Create a new unified feed loader backed by an HTTP client.
     */
    fun create(
      bookFormatSupport: BookFormatSupportType,
      exec: ExecutorService,
      http: LSHTTPClientType,
    ): FeedLoaderType {
      return FeedLoader(
        bookFormatSupport = bookFormatSupport,
        exec = exec,
        fetch = httpFetch(http),
      )
    }

    /**
     * Create a [FeedFetchType] backed by an [LSHTTPClientType].
     */
    fun httpFetch(http: LSHTTPClientType): FeedFetchType {
      return FeedFetchType { uri, credentials, version ->
        val auth = AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials)

        var builder: LSHTTPRequestBuilderType =
          http
            .newRequest(uri)
            .setAuthorization(auth)
            .addBasicTokenPropertiesIfApplicable(credentials)
            .setMethod(LSHTTPRequestBuilderType.Method.Get)

        when (version) {
          FeedFetchVersion.OPDS_2_0 -> {
            builder = builder.addHeader("Accept", StandardFormatNames.opds20Feed.fullType)
          }

          FeedFetchVersion.OPDS_1_2 -> {
            // no Accept header: the server serves its default (OPDS 1.2) representation
          }
        }

        val request = builder.build()
        val response = request.execute()

        val status = response.status
        when {
          status is LSHTTPResponseStatus.Responded.OK -> {
            val bodyStream: InputStream
            val rawStream = status.bodyStream
            if (rawStream == null) {
              bodyStream = ByteArrayInputStream(ByteArray(0))
            } else {
              bodyStream = rawStream
            }
            FeedFetchResult(
              body = bodyStream,
              accessToken = status.getAccessToken(),
              contentType = status.properties.contentType,
            )
          }

          status is LSHTTPResponseStatus.Responded.Error -> {
            throw FeedHTTPTransportException(
              message = status.properties.message,
              code = status.properties.status,
              report = status.properties.problemReport,
            )
          }

          status is LSHTTPResponseStatus.Failed -> {
            throw IOException(
              "Connection failed",
              status.exception,
            )
          }

          else -> {
            throw IOException(
              "Unexpected HTTP response status: $status",
            )
          }
        }
      }
    }
  }
}
