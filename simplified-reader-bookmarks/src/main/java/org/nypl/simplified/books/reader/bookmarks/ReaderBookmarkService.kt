package org.nypl.simplified.books.reader.bookmarks

import org.nypl.simplified.books.reader.bookmarks.internal.RBService
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkServiceProviderType
import org.nypl.simplified.reader.bookmarks.api.ReaderBookmarkServiceType

object ReaderBookmarkService : ReaderBookmarkServiceProviderType {

  override fun createService(
    requirements: ReaderBookmarkServiceProviderType.Requirements
  ): ReaderBookmarkServiceType {
    return RBService(
      threads = requirements.threads,
      httpCalls = requirements.httpCalls,
      bookmarkEventsOut = requirements.events,
      profilesController = requirements.profilesController
    )
  }
}
