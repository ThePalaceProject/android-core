package org.nypl.simplified.tests.mocking

import com.google.common.util.concurrent.FluentFuture
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.borrowing.SAMLDownloadContext
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.taskrecorder.api.TaskResult

class FakeBooksController : BooksControllerType {

  override fun bookBorrow(
    accountID: AccountID,
    bookID: BookID,
    entry: OPDSAcquisitionFeedEntry,
    samlDownloadContext: SAMLDownloadContext?
  ): FluentFuture<TaskResult<*>> {
    TODO("Not yet implemented")
  }

  override fun bookBorrowFailedDismiss(
    accountID: AccountID,
    bookID: BookID
  ) {
    TODO("Not yet implemented")
  }

  override fun bookCancelDownloadAndDelete(
    accountID: AccountID,
    bookID: BookID
  ): FluentFuture<TaskResult<Unit>> {
    TODO("Not yet implemented")
  }

  override fun bookReport(
    accountID: AccountID,
    feedEntry: FeedEntry.FeedEntryOPDS,
    reportType: String
  ): FluentFuture<TaskResult<Unit>> {
    TODO("Not yet implemented")
  }

  override fun booksSync(accountID: AccountID): FluentFuture<TaskResult<Unit>> {
    TODO("Not yet implemented")
  }

  override fun bookRevoke(
    accountID: AccountID,
    bookId: BookID,
  ): FluentFuture<TaskResult<Unit>> {
    TODO("Not yet implemented")
  }

  override fun bookDelete(
    accountID: AccountID,
    bookId: BookID
  ): FluentFuture<TaskResult<Unit>> {
    TODO("Not yet implemented")
  }

  override fun bookRevokeFailedDismiss(
    accountID: AccountID,
    bookID: BookID
  ): FluentFuture<TaskResult<Unit>> {
    TODO("Not yet implemented")
  }
}
