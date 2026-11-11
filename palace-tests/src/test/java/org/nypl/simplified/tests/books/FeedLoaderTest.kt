package org.nypl.simplified.tests.books

import com.google.common.util.concurrent.ListeningExecutorService
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.books.formats.BookFormatSupport
import org.nypl.simplified.books.formats.BookFormatSupportParameters
import org.nypl.simplified.content.api.ContentResolverType
import org.nypl.simplified.feeds.FeedFetchResult
import org.nypl.simplified.feeds.FeedFetchType
import org.nypl.simplified.feeds.FeedLoader
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSSearchParser
import java.net.URI

class FeedLoaderTest : FeedLoaderContract() {

  override fun createFeedLoader(exec: ListeningExecutorService): FeedLoaderType {
    val entryParser =
      OPDSAcquisitionFeedEntryParser.newParser()
    val parser =
      OPDSFeedParser.newParser(entryParser)

    val searchParser = OPDSSearchParser.newParser()
    val bookFormatSupport =
      BookFormatSupport.create(
        BookFormatSupportParameters(
          supportsPDF = false,
          supportsLCP = false,
          supportsAdobeDRM = false,
          supportsBoundless = false,
          supportsAudioBooks = null
        )
      )

    return FeedLoader(
      bookFormatSupport = bookFormatSupport,
      exec = exec,
      fetch = FeedFetchType { uri, _, _ ->
        FeedFetchResult(body = uri.toURL().openStream(), accessToken = null, contentType = null)
      },
    )
  }

  override fun resource(name: String): URI {
    return FeedLoaderContract::class.java.getResource(name)!!.toURI()
  }
}
