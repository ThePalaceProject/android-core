package org.nypl.simplified.feeds.opds12

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.content.api.ContentResolverType
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderSuccess
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeed
import org.nypl.simplified.opds.core.OPDSAcquisitionPath
import org.nypl.simplified.opds.core.OPDSAcquisitionPaths
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.opds.core.OPDSFeedTransportType
import org.nypl.simplified.opds.core.OPDSSearchParserType
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.net.URI
import java.util.SortedMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The OPDS 1.2 implementation of [FeedLoaderType].
 */
class OPDS12FeedLoader private constructor(
  private val bookFormatSupport: BookFormatSupportType,
  private val contentResolver: ContentResolverType,
  private val exec: ExecutorService,
  private val parser: OPDSFeedParserType,
  private val searchParser: OPDSSearchParserType,
  private val transport: OPDSFeedTransportType<AccountAuthenticationCredentials?>
) : FeedLoaderType {
  private val log = LoggerFactory.getLogger(OPDS12FeedLoader::class.java)

  private val filterFlag = AtomicBoolean(true)
  private val wantsOpds20Flag = AtomicBoolean(false)

  override var showOnlySupportedBooks: Boolean
    get() = this.filterFlag.get()
    set(value) {
      this.filterFlag.set(value)
    }

  override var wantsOpds20: Boolean
    get() = this.wantsOpds20Flag.get()
    set(value) {
      this.wantsOpds20Flag.set(value)
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

      val opdsFeedResponse = this.transport.getStream(credentials, uri, method)
      val opdsFeed = opdsFeedResponse.first.use { stream -> this.parser.parse(uri, stream) }
      val search = this.fetchSearchLink(opdsFeed, credentials, method)
      val feed =
        OPDS12FeedConverter.convert(
          accountID = accountId,
          feed = opdsFeed,
          filter = this::isEntrySupported,
          search = search
        )

      return FeedLoaderSuccess(feed, opdsFeedResponse.second)
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

  private fun isEntrySupported(entry: org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry): Boolean {
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
              feed = this.parser.parse(uri, stream),
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

  private fun errorAttributesOf(
    uri: URI,
    method: String
  ): SortedMap<String, String> =
    sortedMapOf(
      Pair("Feed", uri.toString()),
      Pair("Method", method)
    )

  private fun fetchSearchLink(
    opdsFeed: OPDSAcquisitionFeed,
    credentials: AccountAuthenticationCredentials?,
    method: String
  ): org.nypl.simplified.opds.core.OPDSOpenSearch1_1? {
    val searchLink = opdsFeed.feedSearchURI
    return if (searchLink != null) {
      val response = this.transport.getStream(credentials, searchLink.uri, method)
      response.first.use { stream ->
        this.searchParser.parse(searchLink.uri, stream)
      }
    } else {
      null
    }
  }

  companion object {
    /**
     * Create a new OPDS 1.2 feed loader.
     */
    fun create(
      bookFormatSupport: BookFormatSupportType,
      contentResolver: ContentResolverType,
      exec: ExecutorService,
      parser: OPDSFeedParserType,
      searchParser: OPDSSearchParserType,
      transport: OPDSFeedTransportType<AccountAuthenticationCredentials?>
    ): FeedLoaderType =
      OPDS12FeedLoader(
        bookFormatSupport = bookFormatSupport,
        contentResolver = contentResolver,
        exec = exec,
        parser = parser,
        searchParser = searchParser,
        transport = transport
      )
  }
}
