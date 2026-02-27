package org.nypl.simplified.tests.books

import com.google.common.util.concurrent.ListeningExecutorService
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.books.formats.BookFormatSupport
import org.nypl.simplified.books.formats.BookFormatSupportParameters
import org.nypl.simplified.content.api.ContentResolverType
import org.nypl.simplified.feeds.api.FeedLoader
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSFeedTransportType
import org.nypl.simplified.opds.core.OPDSSearchParser
import java.net.URI

class FeedLoaderTest : FeedLoaderContract() {

  override fun createFeedLoader(exec: ListeningExecutorService): FeedLoaderType {
    val entryParser =
      OPDSAcquisitionFeedEntryParser.newParser()
    val parser =
      OPDSFeedParser.newParser(entryParser)
    val transport =
      OPDSFeedTransportType<AccountAuthenticationCredentials?> { context, uri, method ->
        uri.toURL().openStream() to ""
      }

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

    val contentResolver =
      Mockito.mock(ContentResolverType::class.java)

    return FeedLoader.create(
      bookFormatSupport = bookFormatSupport,
      contentResolver = contentResolver,
      exec = exec,
      parser = parser,
      searchParser = searchParser,
      transport = transport
    )
  }

  override fun resource(name: String): URI {
    return FeedLoaderContract::class.java.getResource(name)!!.toURI()
  }
}
