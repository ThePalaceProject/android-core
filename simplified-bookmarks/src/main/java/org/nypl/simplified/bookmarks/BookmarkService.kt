package org.nypl.simplified.bookmarks

import org.nypl.simplified.bookmarks.api.BookmarkServiceProviderType
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.nypl.simplified.bookmarks.internal.BService

object BookmarkService : BookmarkServiceProviderType {

  override fun createService(
    requirements: BookmarkServiceProviderType.Requirements
  ): BookmarkServiceType {
    return BService(
      threads = requirements.threads,
      httpCalls = requirements.httpCalls,
      bookmarkEventsOut = requirements.events,
      profilesController = requirements.profilesController
    )
  }
}
