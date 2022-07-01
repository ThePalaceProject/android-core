package org.nypl.simplified.tests.books.bookmarks

import io.reactivex.subjects.Subject
import org.nypl.simplified.bookmarks.BookmarkService
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.bookmarks.api.BookmarkEvent
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.bookmarks.api.BookmarkServiceProviderType
import org.nypl.simplified.bookmarks.api.BookmarkServiceType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BookmarkServiceTest : BookmarkServiceContract() {

  override val logger: Logger = LoggerFactory.getLogger(BookmarkServiceTest::class.java)

  override fun bookmarkService(
    threads: (Runnable) -> Thread,
    events: Subject<BookmarkEvent>,
    httpCalls: BookmarkHTTPCallsType,
    profilesController: ProfilesControllerType
  ): BookmarkServiceType {
    return BookmarkService.createService(
      BookmarkServiceProviderType.Requirements(
        threads = threads,
        events = events,
        httpCalls = httpCalls,
        profilesController = profilesController
      )
    )
  }
}
