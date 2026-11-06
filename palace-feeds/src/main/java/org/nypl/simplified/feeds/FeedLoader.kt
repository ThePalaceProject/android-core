package org.nypl.simplified.feeds

import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addBasicTokenPropertiesIfApplicable
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.content.api.ContentResolverType
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderSuccess
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.feeds.opds12.OPDS12FeedConverter
import org.nypl.simplified.feeds.opds12.OPDS12FeedHTTPTransportException
import org.nypl.simplified.feeds.opds20.OPDS20FeedConverter
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionPath
import org.nypl.simplified.opds.core.OPDSAcquisitionPaths
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.opds.core.OPDSFeedTransportIOException
import org.nypl.simplified.opds.core.OPDSSearchLink
import org.nypl.simplified.opds.core.OPDSSearchParserType
import org.nypl.simplified.opds.core.OPDSOpenSearch1_1
import org.readium.r2.opds.OPDS2Parser
import org.readium.r2.shared.util.Url
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.SortedMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A functional interface for fetching feed content from a URI.
 *
 * @param uri The URI to fetch
 * @param credentials HTTP credentials, if any
 * @param acceptHeader The Accept header value, or null if not set
 * @return A pair of the response body stream and the access token (or null)
 */
fun interface FeedFetchType {
  fun fetch(
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    acceptHeader: String?
  ): Pair<InputStream, String?>
}

/**
 * A unified feed loader that handles both OPDS 1.2 and OPDS 2.0 serializations.
 *
 * The [wantsOpds20] flag controls whether the loader sends an
 * `Accept: application/opds+json` header to the server. When `true`, the
 * response is parsed as OPDS 2.0 JSON using the Readium parser. When `false`
 * (the default), the response is parsed as OPDS 1.2 Atom/XML.
 */
class FeedLoader(
  private val bookFormatSupport: BookFormatSupportType,
  private val contentResolver: ContentResolverType,
  private val exec: ExecutorService,
  private val fetch: FeedFetchType,
  private val opds12Parser: OPDSFeedParserType,
  private val opds12SearchParser: OPDSSearchParserType,
) : FeedLoaderType {
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
    method: String
  ): CompletableFuture<FeedLoaderResult> {
    val future = CompletableFuture<FeedLoaderResult>()
    this.exec.execute {
      try {
        future.complete(
          this.fetchSynchronously(
            accountId = accountID,
            uri = uri,
            credentials = credentials,
            method = method
          )
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
    method: String
  ): FeedLoaderResult {
    try {
      if (uri.scheme == "content") {
        return this.parseFromContentResolver(accountId, uri)
      }

      return if (this.preferOPDS20) {
        this.fetchOPDS20(accountId, uri, credentials, method)
      } else {
        this.fetchOPDS12(accountId, uri, credentials, method)
      }
    } catch (e: OPDS12FeedHTTPTransportException) {
      this.log.debug("feed transport exception: ", e)

      if (e.code == 401) {
        return FeedLoaderFailure.FeedLoaderFailedAuthentication(
          problemReport = e.report,
          exception = e,
          attributesInitial = this.errorAttributesOf(uri, method),
          message = e.localizedMessage ?: ""
        )
      }
      return FeedLoaderFailure.FeedLoaderFailedGeneral(
        problemReport = e.report,
        exception = e,
        attributesInitial = this.errorAttributesOf(uri, method),
        message = e.localizedMessage ?: ""
      )
    } catch (e: Exception) {
      this.log.debug("feed exception: ", e)

      return FeedLoaderFailure.FeedLoaderFailedGeneral(
        problemReport = null,
        exception = e,
        attributesInitial = this.errorAttributesOf(uri, method),
        message = e.localizedMessage ?: ""
      )
    }
  }

  /**
   * Fetch and parse an OPDS 1.2 feed.
   */
  private fun fetchOPDS12(
    accountId: AccountID,
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    method: String
  ): FeedLoaderResult {
    val response = this.fetch.fetch(uri, credentials, null)
    val body = response.first
    val accessToken = response.second

    val opdsFeed = body.use { stream -> this.opds12Parser.parse(uri, stream) }
    val search = this.fetchSearchLink(opdsFeed.feedSearchURI, credentials, method)

    val feed =
      OPDS12FeedConverter.convert(
        accountID = accountId,
        feed = opdsFeed,
        filter = this::isEntrySupported,
        search = search
      )

    return FeedLoaderSuccess(feed, accessToken)
  }

  /**
   * Fetch and parse an OPDS 2.0 feed.
   */
  private fun fetchOPDS20(
    accountId: AccountID,
    uri: URI,
    credentials: AccountAuthenticationCredentials?,
    method: String
  ): FeedLoaderResult {
    val response = this.fetch.fetch(uri, credentials, ACCEPT_OPDS_JSON)
    val body = response.first
    val accessToken = response.second

    val bytes = body.readBytes()
    val baseUrl = Url(uri.toString())!!
    val parseData = OPDS2Parser.parse(bytes, baseUrl)
    val readiumFeed = parseData.feed
      ?: throw IOException("Response did not contain an OPDS 2.0 feed")

    val feed = OPDS20FeedConverter.convert(accountId, readiumFeed)
    return FeedLoaderSuccess(feed, accessToken)
  }

  private fun isEntrySupported(entry: OPDSAcquisitionFeedEntry): Boolean {
    if (!this.showOnlySupportedBooks) {
      return true
    }

    val linearizedPaths = OPDSAcquisitionPaths.linearize(entry)
    for (path in linearizedPaths) {
      val relationSupported = this.isRelationSupported(path.source.relation)
      val typePathSupported = this.isTypePathSupported(path)
      if (relationSupported && typePathSupported) {
        return true
      }
    }
    return false
  }

  private fun isRelationSupported(relation: OPDSAcquisition.Relation): Boolean =
    when (relation) {
      OPDSAcquisition.Relation.ACQUISITION_BORROW -> true
      OPDSAcquisition.Relation.ACQUISITION_BUY -> false
      OPDSAcquisition.Relation.ACQUISITION_GENERIC -> true
      OPDSAcquisition.Relation.ACQUISITION_OPEN_ACCESS -> true
      OPDSAcquisition.Relation.ACQUISITION_SAMPLE -> false
      OPDSAcquisition.Relation.ACQUISITION_SUBSCRIBE -> false
    }

  private fun isTypePathSupported(path: OPDSAcquisitionPath): Boolean =
    this.bookFormatSupport.isSupportedPath(path.asMIMETypes())

  private fun parseFromContentResolver(
    accountId: AccountID,
    uri: URI
  ): FeedLoaderResult {
    val streamMaybe = this.contentResolver.openInputStream(uri)
    return if (streamMaybe != null) {
      streamMaybe.use { stream ->
        FeedLoaderSuccess(
          feed =
            OPDS12FeedConverter.convert(
              accountID = accountId,
              feed = this.opds12Parser.parse(uri, stream),
              search = null,
              filter = this::isEntrySupported
            ),
          accessToken = null
        )
      }
    } else {
      FeedLoaderFailure.FeedLoaderFailedGeneral(
        problemReport = null,
        exception = FileNotFoundException(),
        message = "File not found!",
        attributesInitial =
          sortedMapOf(Pair("URI", uri.toASCIIString()))
      )
    }
  }

  private fun fetchSearchLink(
    searchLink: OPDSSearchLink?,
    credentials: AccountAuthenticationCredentials?,
    method: String
  ): OPDSOpenSearch1_1? {
    return if (searchLink != null) {
      val response = this.fetch.fetch(searchLink.uri, credentials, null)
      response.first.use { stream ->
        this.opds12SearchParser.parse(searchLink.uri, stream)
      }
    } else {
      null
    }
  }

  private fun errorAttributesOf(
    uri: URI,
    method: String
  ): SortedMap<String, String> =
    sortedMapOf(
      Pair("Feed", uri.toString()),
      Pair("Method", method)
    )

  companion object {
    private const val ACCEPT_OPDS_JSON = "application/opds+json"

    /**
     * Create a new unified feed loader backed by an HTTP client.
     */
    fun create(
      bookFormatSupport: BookFormatSupportType,
      contentResolver: ContentResolverType,
      exec: ExecutorService,
      http: LSHTTPClientType,
      opds12Parser: OPDSFeedParserType,
      opds12SearchParser: OPDSSearchParserType,
    ): FeedLoaderType =
      FeedLoader(
        bookFormatSupport = bookFormatSupport,
        contentResolver = contentResolver,
        exec = exec,
        fetch = httpFetch(http),
        opds12Parser = opds12Parser,
        opds12SearchParser = opds12SearchParser,
      )

    /**
     * Create a [FeedFetchType] backed by an [LSHTTPClientType].
     */
    fun httpFetch(http: LSHTTPClientType): FeedFetchType =
      FeedFetchType { uri, credentials, acceptHeader ->
        val auth = AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials)

        var builder: LSHTTPRequestBuilderType =
          http
            .newRequest(uri)
            .setAuthorization(auth)
            .addBasicTokenPropertiesIfApplicable(credentials)
            .setMethod(LSHTTPRequestBuilderType.Method.Get)

        if (acceptHeader != null) {
          builder = builder.addHeader("Accept", acceptHeader)
        }

        val request = builder.build()
        val response = request.execute()

        val status = response.status
        when {
          status is LSHTTPResponseStatus.Responded.OK -> {
            (status.bodyStream ?: ByteArrayInputStream(ByteArray(0))) to status.getAccessToken()
          }

          status is LSHTTPResponseStatus.Responded.Error -> {
            throw OPDS12FeedHTTPTransportException(
              message = status.properties.message,
              code = status.properties.status,
              report = status.properties.problemReport
            )
          }

          status is LSHTTPResponseStatus.Failed -> {
            throw OPDSFeedTransportIOException(
              message = "Connection failed",
              cause = IOException(status.exception)
            )
          }

          else -> {
            throw OPDSFeedTransportIOException(
              message = "Unexpected HTTP response status",
              cause = IOException("Status: $status")
            )
          }
        }
      }
  }
}
