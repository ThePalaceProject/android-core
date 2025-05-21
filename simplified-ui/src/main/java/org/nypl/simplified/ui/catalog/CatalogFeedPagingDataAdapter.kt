package org.nypl.simplified.ui.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.MoreExecutors
import io.reactivex.disposables.Disposable
import org.joda.time.DateTime
import org.librarysimplified.ui.R
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatus.DownloadExternalAuthenticationInProgress
import org.nypl.simplified.books.book_registry.BookStatus.DownloadWaitingForExternalAuthentication
import org.nypl.simplified.books.book_registry.BookStatus.Downloading
import org.nypl.simplified.books.book_registry.BookStatus.FailedDownload
import org.nypl.simplified.books.book_registry.BookStatus.FailedLoan
import org.nypl.simplified.books.book_registry.BookStatus.FailedRevoke
import org.nypl.simplified.books.book_registry.BookStatus.Held
import org.nypl.simplified.books.book_registry.BookStatus.Holdable
import org.nypl.simplified.books.book_registry.BookStatus.Loanable
import org.nypl.simplified.books.book_registry.BookStatus.Loaned
import org.nypl.simplified.books.book_registry.BookStatus.ReachedLoanLimit
import org.nypl.simplified.books.book_registry.BookStatus.RequestingDownload
import org.nypl.simplified.books.book_registry.BookStatus.RequestingLoan
import org.nypl.simplified.books.book_registry.BookStatus.RequestingRevoke
import org.nypl.simplified.books.book_registry.BookStatus.Revoked
import org.nypl.simplified.books.book_registry.BookStatusEvent.BookStatusEventChanged
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.opds.core.OPDSAvailabilityHeld
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.OPDSAvailabilityMatcherType
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSAvailabilityRevoked
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult

class CatalogFeedPagingDataAdapter(
  private val covers: BookCoverProviderType,
  private val profiles: ProfilesControllerType,
  private val buttonCreator: CatalogButtons,
  private val registryEvents: CatalogBookRegistryEvents,
  private val onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit,
  private val onBookErrorDismiss: (CatalogBookStatus<*>) -> Unit,
  private val onBookBorrow: (CatalogBorrowParameters) -> Unit,
  private val onBookRevoke: (CatalogBookStatus<*>) -> Unit,
  private val onBookViewerOpen: (Book, BookFormat) -> Unit,
  private val onBookDelete: (CatalogBookStatus<*>) -> Unit,
  private val onShowTaskError: (TaskResult.Failure<*>) -> Unit,
) : PagingDataAdapter<FeedEntry, CatalogFeedPagingDataAdapter.ViewHolder>(diffCallback) {

  companion object {
    private val diffCallback =
      object : DiffUtil.ItemCallback<FeedEntry>() {
        override fun areContentsTheSame(
          oldItem: FeedEntry,
          newItem: FeedEntry
        ): Boolean {
          return oldItem == newItem
        }

        override fun areItemsTheSame(
          oldItem: FeedEntry,
          newItem: FeedEntry
        ): Boolean {
          return oldItem.bookID == newItem.bookID
        }
      }
  }

  inner class ViewHolder(
    private val view: View
  ) : RecyclerView.ViewHolder(view) {

    private var subscription: Disposable? = null
    private var feedEntry: FeedEntry? = null

    private val buttonCreator =
      this@CatalogFeedPagingDataAdapter.buttonCreator

    private val idle =
      this.view.findViewById<ViewGroup>(R.id.bookCellIdle)
    private val corrupt =
      this.view.findViewById<ViewGroup>(R.id.bookCellCorrupt)
    private val error =
      this.view.findViewById<ViewGroup>(R.id.bookCellError)
    private val progress =
      this.view.findViewById<ViewGroup>(R.id.bookCellInProgress)

    private val idleCover =
      this.view.findViewById<ImageView>(R.id.bookCellIdleCover)
    private val idleCoverProgress =
      this.view.findViewById<ProgressBar>(R.id.bookCellIdleCoverProgress)
    private val idleTitle =
      this.idle.findViewById<TextView>(R.id.bookCellIdleTitle)
    private val idleMeta =
      this.idle.findViewById<TextView>(R.id.bookCellIdleMeta)
    private val idleAuthor =
      this.idle.findViewById<TextView>(R.id.bookCellIdleAuthor)
    private val idleButtons =
      this.idle.findViewById<ViewGroup>(R.id.bookCellIdleButtons)

    private val progressProgress =
      this.view.findViewById<ProgressBar>(R.id.bookCellInProgressBar)
    private val progressText =
      this.view.findViewById<TextView>(R.id.bookCellInProgressTitle)

    private val errorTitle =
      this.error.findViewById<TextView>(R.id.bookCellErrorTitle)
    private val errorDismiss =
      this.error.findViewById<Button>(R.id.bookCellErrorButtonDismiss)
    private val errorDetails =
      this.error.findViewById<Button>(R.id.bookCellErrorButtonDetails)
    private val errorRetry =
      this.error.findViewById<Button>(R.id.bookCellErrorButtonRetry)

    fun unbind() {
      this.subscription?.dispose()
      this.feedEntry = null

      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, false)
      this.setVisible(this.progress, false)

      this.errorDetails.setOnClickListener(null)
      this.errorDismiss.setOnClickListener(null)
      this.errorRetry.setOnClickListener(null)
      this.idle.setOnClickListener(null)
      this.idleAuthor.text = null
      this.idleButtons.removeAllViews()
      this.idleCover.contentDescription = null
      this.idleCover.setImageDrawable(null)
      this.idleCover.setOnClickListener(null)
      this.idleTitle.setOnClickListener(null)
      this.idleTitle.text = null
      this.progress.setOnClickListener(null)
      this.progressText.setOnClickListener(null)
    }

    fun bind(
      item: FeedEntry
    ) {
      this.feedEntry = item
      this.setVisible(this.progress, false)
      this.setVisible(this.idle, true)
      this.setVisible(this.error, false)
      this.setVisible(this.corrupt, false)

      val targetHeight =
        this.view.resources.getDimensionPixelSize(R.dimen.catalogBookThumbnailHeight)
      val targetWidth = 0

      when (item) {
        is FeedEntry.FeedEntryCorrupt -> {
          this.view.setOnClickListener {
            // Nothing!
          }
        }

        is FeedEntry.FeedEntryOPDS -> {
          this.subscription =
            this@CatalogFeedPagingDataAdapter.registryEvents.events
              .ofType(BookStatusEventChanged::class.java)
              .filter { event -> event.bookId == item.bookID }
              .subscribe { event -> this.onStatusChangedForFeedEntry(item) }

          this.view.setOnClickListener {
            this@CatalogFeedPagingDataAdapter.onBookSelected(item)
          }

          this.setVisible(this.idleCover, false)
          this.setVisible(this.idleCoverProgress, true)

          this.errorTitle.text = item.feedEntry.title
          this.idleTitle.text = item.feedEntry.title
          this.idleAuthor.text = item.feedEntry.authorsCommaSeparated

          val f =
            this@CatalogFeedPagingDataAdapter.covers.loadThumbnailInto(
              entry = item,
              imageView = this.idleCover,
              width = targetWidth,
              height = targetHeight
            )

          f.addListener({
            this.setVisible(this.idleCover, true)
            this.setVisible(this.idleCoverProgress, false)
          }, MoreExecutors.directExecutor())

          this.onStatusChangedForFeedEntry(item)
        }
      }
    }

    private fun onStatusChangedForFeedEntry(
      item: FeedEntry.FeedEntryOPDS
    ) {
      val status =
        CatalogBookStatus.create(
          this@CatalogFeedPagingDataAdapter.registryEvents.registry,
          item
        )

      this.onStatusChanged(BookWithStatus(status.book, status.status))
    }

    private fun setVisible(
      target: View,
      visible: Boolean
    ) {
      /*
       * Setting the visibility of a view in Android has a cost, even if that view is already in the desired
       * visibility state. Therefore, we don't try to set the visibility of a view if the view is already
       * in the right state.
       */
      when (target.visibility) {
        VISIBLE -> {
          if (!visible) {
            target.visibility = INVISIBLE
          }
        }

        INVISIBLE -> {
          if (visible) {
            target.visibility = VISIBLE
          }
        }

        GONE -> {
          if (visible) {
            target.visibility = VISIBLE
          }
        }
      }
    }

    private fun onStatusChanged(
      bookWithStatus: BookWithStatus
    ) {
      return when (val status = bookWithStatus.status) {
        is DownloadExternalAuthenticationInProgress -> {
          this.onStatusChangedDownloadExternalAuthenticationInProgress(bookWithStatus.book)
        }

        is DownloadWaitingForExternalAuthentication -> {
          this.onStatusDownloadWaitingForExternalAuthentication(bookWithStatus.book)
        }

        is Downloading -> {
          this.onBookStatusDownloading(bookWithStatus.book, status)
        }

        is FailedDownload -> {
          this.onBookStatusFailedDownload(bookWithStatus.book, status)
        }

        is FailedLoan -> {
          this.onBookStatusFailedLoan(bookWithStatus.book, status)
        }

        is FailedRevoke -> {
          this.onBookStatusFailedRevoke(bookWithStatus.book, status)
        }

        is Held.HeldInQueue -> {
          this.onBookStatusHeldInQueue(bookWithStatus.book, status)
        }

        is Held.HeldReady -> {
          this.onBookStatusHeldReady(bookWithStatus.book, status)
        }

        is Holdable -> {
          this.onBookStatusHoldable(bookWithStatus.book, status)
        }

        is Loanable -> {
          this.onBookStatusLoanable(bookWithStatus.book, status)
        }

        is Loaned.LoanedDownloaded -> {
          this.onBookStatusLoanedDownloaded(bookWithStatus.book, status)
        }

        is Loaned.LoanedNotDownloaded -> {
          this.onBookStatusLoanedNotDownloaded(bookWithStatus.book, status)
        }

        is ReachedLoanLimit -> {
          this.onBookStatusReachedLoanLimit(bookWithStatus.book, status)
        }

        is RequestingDownload -> {
          this.onBookStatusRequestingDownload(bookWithStatus.book, status)
        }

        is RequestingLoan -> {
          this.onBookStatusRequestingLoan(bookWithStatus.book, status)
        }

        is RequestingRevoke -> {
          this.onBookStatusRequestingRevoke(bookWithStatus.book, status)
        }

        is Revoked -> {
          this.onBookStatusRevoked(bookWithStatus.book, status)
        }
      }
    }

    private fun onStatusDownloadWaitingForExternalAuthentication(
      book: Book
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, false)
      this.setVisible(this.progress, true)

      this.progressText.text = book.entry.title
      this.progressProgress.isIndeterminate = true
    }

    private fun onStatusChangedDownloadExternalAuthenticationInProgress(
      book: Book
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, false)
      this.setVisible(this.progress, true)

      this.progressText.text = book.entry.title
      this.progressProgress.isIndeterminate = true
    }

    private fun onBookStatusDownloading(
      book: Book,
      status: Downloading
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, false)
      this.setVisible(this.progress, true)

      this.progressText.text = book.entry.title

      val progressPercent = status.progressPercent?.toInt()
      if (progressPercent != null) {
        this.progressProgress.isIndeterminate = false
        this.progressProgress.progress = progressPercent
      } else {
        this.progressProgress.isIndeterminate = true
      }
    }

    private fun onBookStatusFailedDownload(
      book: Book,
      status: FailedDownload
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, true)
      this.setVisible(this.idle, false)
      this.setVisible(this.progress, false)

      this.errorDismiss.setOnClickListener {
        this.onBookErrorDismiss(book, status)
      }
      this.errorDetails.setOnClickListener {
        this@CatalogFeedPagingDataAdapter.onShowTaskError(status.result)
      }
      this.errorRetry.setOnClickListener {
        this.onBookBorrow(book)
      }
    }

    private fun onBookErrorDismiss(
      book: Book,
      status: BookStatus
    ) {
      this@CatalogFeedPagingDataAdapter.onBookErrorDismiss(
        CatalogBookStatus(book, status, previewStatus = BookPreviewStatus.None)
      )
    }

    private fun onBookRevoke(
      book: Book,
      status: BookStatus
    ) {
      this@CatalogFeedPagingDataAdapter.onBookRevoke(
        CatalogBookStatus(book, status, previewStatus = BookPreviewStatus.None)
      )
    }

    private fun onBookViewerOpen(
      book: Book,
      bookFormat: BookFormat
    ) {
      this@CatalogFeedPagingDataAdapter.onBookViewerOpen(book, bookFormat)
    }

    private fun onBookDelete(
      book: Book,
      status: BookStatus
    ) {
      this@CatalogFeedPagingDataAdapter.onBookDelete(
        CatalogBookStatus(book, status, previewStatus = BookPreviewStatus.None)
      )
    }

    private fun onBookBorrow(
      book: Book
    ) {
      this@CatalogFeedPagingDataAdapter.onBookBorrow(
        CatalogBorrowParameters(
          accountID = book.account,
          bookID = book.id,
          entry = book.entry,
          samlDownloadContext = null
        )
      )
    }

    private fun onBookStatusFailedLoan(
      book: Book,
      status: FailedLoan
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, true)
      this.setVisible(this.idle, false)
      this.setVisible(this.progress, false)

      this.errorTitle.visibility = View.VISIBLE
      this.errorDismiss.setOnClickListener {
        this.onBookErrorDismiss(book, status)
      }
      this.errorDetails.setOnClickListener {
        this@CatalogFeedPagingDataAdapter.onShowTaskError(status.result)
      }
      this.errorRetry.setOnClickListener {
        this.onBookBorrow(book)
      }
    }

    private fun onBookStatusFailedRevoke(
      book: Book,
      status: FailedRevoke
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, true)
      this.setVisible(this.idle, false)
      this.setVisible(this.progress, false)

      this.errorTitle.visibility = View.VISIBLE
      this.errorDismiss.setOnClickListener {
        this.onBookErrorDismiss(book, status)
      }
      this.errorDetails.setOnClickListener {
        this@CatalogFeedPagingDataAdapter.onShowTaskError(status.result)
      }
      this.errorRetry.setOnClickListener {
        this.onBookRevoke(book, status)
      }
    }

    private fun onBookStatusHeldInQueue(
      book: Book,
      status: Held.HeldInQueue
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, true)
      this.setVisible(this.progress, false)

      this.idleButtons.removeAllViews()
      if (status.isRevocable) {
        this.idleButtons.addView(
          this.buttonCreator.createRevokeHoldButton(
            onClick = {
              this.onBookRevoke(book, status)
            }
          )
        )
      } else {
        this.idleButtons.addView(
          this.buttonCreator.createCenteredTextForButtons(
            R.string.catalogHoldCannotCancel
          )
        )
      }
    }

    private fun onBookStatusHeldReady(
      book: Book,
      status: Held.HeldReady
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, true)
      this.setVisible(this.progress, false)

      this.idleButtons.removeAllViews()
      this.idleButtons.addView(
        this.buttonCreator.createGetButton(
          onClick = {
            this.onBookBorrow(book)
          }
        )
      )

      if (status.isRevocable) {
        this.idleButtons.addView(
          this.buttonCreator.createButtonSpace()
        )
        this.idleButtons.addView(
          this.buttonCreator.createRevokeHoldButton(
            onClick = {
              this.onBookRevoke(book, status)
            }
          )
        )
      }
    }

    private fun onBookStatusHoldable(
      book: Book,
      status: Holdable
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, true)
      this.setVisible(this.progress, false)

      this.idleButtons.removeAllViews()
      this.idleButtons.addView(
        this.buttonCreator.createReserveButton(
          onClick = {
            this.onBookBorrow(book)
          }
        )
      )
    }

    private fun onBookStatusLoanable(
      book: Book,
      status: Loanable
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, true)
      this.setVisible(this.progress, false)

      this.idleButtons.removeAllViews()
      this.idleButtons.addView(
        this.buttonCreator.createGetButton(
          onClick = {
            this.onBookBorrow(book)
          }
        )
      )
    }

    private fun onBookStatusLoanedDownloaded(
      book: Book,
      status: Loaned.LoanedDownloaded
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, true)
      this.setVisible(this.progress, false)

      this.idleButtons.removeAllViews()

      when (val format = book.findPreferredFormat()) {
        is BookFormat.BookFormatPDF,
        is BookFormat.BookFormatEPUB -> {
          val loanDuration = getLoanDuration(book)
          this.idleButtons.addView(
            if (loanDuration.isNotEmpty()) {
              this.buttonCreator.createReadButtonWithLoanDuration(loanDuration) {
                this.onBookViewerOpen(book, format)
              }
            } else {
              this.buttonCreator.createReadButton(
                onClick = {
                  this.onBookViewerOpen(book, format)
                },
                heightMatchParent = true
              )
            }
          )
        }

        is BookFormat.BookFormatAudioBook -> {
          val loanDuration = getLoanDuration(book)
          this.idleButtons.addView(
            if (loanDuration.isNotEmpty()) {
              this.buttonCreator.createListenButtonWithLoanDuration(loanDuration) {
                this.onBookViewerOpen(book, format)
              }
            } else {
              this.buttonCreator.createListenButton(
                onClick = {
                  this.onBookViewerOpen(book, format)
                },
                heightMatchParent = true
              )
            }
          )
        }

        null -> {
          this.idleButtons.addView(this.buttonCreator.createButtonSizedSpace())
        }
      }

      if (isBookReturnable(book)) {
        this.idleButtons.addView(this.buttonCreator.createButtonSpace())
        this.idleButtons.addView(
          this.buttonCreator.createRevokeLoanButton(
            onClick = {
              this.onBookRevoke(book, status)
            },
            heightMatchParent = true
          )
        )
      } else if (isBookDeletable(book)) {
        this.idleButtons.addView(this.buttonCreator.createButtonSpace())
        this.idleButtons.addView(
          this.buttonCreator.createRevokeLoanButton(
            onClick = {
              this.onBookDelete(book, status)
            },
            heightMatchParent = true
          )
        )
      }
    }

    private fun onBookStatusLoanedNotDownloaded(
      book: Book,
      status: Loaned.LoanedNotDownloaded
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, true)
      this.setVisible(this.progress, false)

      this.idleButtons.removeAllViews()

      val loanDuration = getLoanDuration(book)

      this.idleButtons.addView(
        when {
          loanDuration.isNotEmpty() -> {
            this.buttonCreator.createDownloadButtonWithLoanDuration(loanDuration) {
              this.onBookBorrow(book)
            }
          }

          status.isOpenAccess -> {
            this.buttonCreator.createGetButton(
              onClick = {
                this.onBookBorrow(book)
              },
              heightMatchParent = true
            )
          }

          else -> {
            this.buttonCreator.createDownloadButton(
              onClick = {
                this.onBookBorrow(book)
              },
              heightMatchParent = true
            )
          }
        }
      )

      if (isBookReturnable(book)) {
        this.idleButtons.addView(this.buttonCreator.createButtonSpace())
        this.idleButtons.addView(
          this.buttonCreator.createRevokeLoanButton(
            onClick = {
              this.onBookRevoke(book, status)
            },
            heightMatchParent = true
          )
        )
      } else if (isBookDeletable(book)) {
        this.idleButtons.addView(this.buttonCreator.createButtonSpace())
        this.idleButtons.addView(
          this.buttonCreator.createRevokeLoanButton(
            onClick = {
              this.onBookDelete(book, status)
            },
            heightMatchParent = true
          )
        )
      }
    }

    private fun onBookStatusReachedLoanLimit(
      book: Book,
      status: ReachedLoanLimit
    ) {
      MaterialAlertDialogBuilder(this.view.context)
        .setTitle(R.string.bookReachedLoanLimitDialogTitle)
        .setMessage(R.string.bookReachedLoanLimitDialogMessage)
        .setPositiveButton(R.string.bookReachedLoanLimitDialogButton) { dialog, _ ->
          dialog.dismiss()
        }
        .create()
        .show()
    }

    private fun onBookStatusRequestingDownload(
      book: Book,
      status: RequestingDownload
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, false)
      this.setVisible(this.progress, true)

      this.progressText.text = book.entry.title
      this.progressProgress.isIndeterminate = true
    }

    private fun onBookStatusRequestingRevoke(
      book: Book,
      status: RequestingRevoke
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, false)
      this.setVisible(this.progress, true)

      this.progressText.text = book.entry.title
      this.progressProgress.isIndeterminate = true
    }

    private fun onBookStatusRequestingLoan(
      book: Book,
      status: RequestingLoan
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, false)
      this.setVisible(this.progress, true)

      this.progressText.text = book.entry.title
      this.progressProgress.isIndeterminate = true
    }

    private fun onBookStatusRevoked(
      book: Book,
      status: Revoked
    ) {
      this.setVisible(this.corrupt, false)
      this.setVisible(this.error, false)
      this.setVisible(this.idle, true)
      this.setVisible(this.progress, false)

      this.idleButtons.removeAllViews()
    }

    /**
     * XXX: This information really should be available somewhere else. This was ported from
     * existing code.
     */

    private fun getLoanDuration(book: Book): String {
      val status = BookStatus.fromBook(book)
      return if (status is Loaned.LoanedDownloaded ||
        status is Loaned.LoanedNotDownloaded
      ) {
        val endDate = (status as? Loaned.LoanedDownloaded)?.loanExpiryDate
          ?: (status as? Loaned.LoanedNotDownloaded)?.loanExpiryDate

        if (
          endDate != null
        ) {
          CatalogBookAvailabilityStrings.intervalStringLoanDuration(
            this.view.context.resources,
            DateTime.now(),
            endDate
          )
        } else {
          ""
        }
      } else {
        ""
      }
    }

    /**
     * XXX: This information really should be available somewhere else. This was ported from
     * existing code.
     */

    private fun isBookReturnable(book: Book): Boolean {
      val profile = this@CatalogFeedPagingDataAdapter.profiles.profileCurrent()
      val account = profile.account(book.account)

      return try {
        if (account.bookDatabase.books().contains(book.id)) {
          when (val status = BookStatus.fromBook(book)) {
            is Loaned.LoanedDownloaded ->
              status.returnable

            is Loaned.LoanedNotDownloaded ->
              true

            else ->
              false
          }
        } else {
          false
        }
      } catch (e: Exception) {
        false
      }
    }

    /**
     * XXX: This information really should be available somewhere else. This was ported from
     * existing code.
     */

    private fun isBookDeletable(book: Book): Boolean {
      return try {
        val profile = this@CatalogFeedPagingDataAdapter.profiles.profileCurrent()
        val account = profile.account(book.account)
        return if (account.bookDatabase.books().contains(book.id)) {
          book.entry.availability.matchAvailability(
            object : OPDSAvailabilityMatcherType<Boolean, Exception> {
              override fun onHeldReady(availability: OPDSAvailabilityHeldReady): Boolean =
                false

              override fun onHeld(availability: OPDSAvailabilityHeld): Boolean =
                false

              override fun onHoldable(availability: OPDSAvailabilityHoldable): Boolean =
                false

              override fun onLoaned(availability: OPDSAvailabilityLoaned): Boolean =
                availability.revoke.isNone && book.isDownloaded

              override fun onLoanable(availability: OPDSAvailabilityLoanable): Boolean =
                true

              override fun onOpenAccess(availability: OPDSAvailabilityOpenAccess): Boolean =
                true

              override fun onRevoked(availability: OPDSAvailabilityRevoked): Boolean =
                false
            })
        } else {
          false
        }
      } catch (e: Exception) {
        false
      }
    }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): ViewHolder {
    val view =
      LayoutInflater.from(parent.context)
        .inflate(R.layout.book_cell, parent, false)

    return this.ViewHolder(view)
  }

  override fun onViewRecycled(
    holder: ViewHolder
  ) {
    holder.unbind()
  }

  override fun onDetachedFromRecyclerView(
    recyclerView: RecyclerView
  ) {
    // Nothing yet.
  }

  override fun onBindViewHolder(
    holder: ViewHolder,
    position: Int
  ) {
    val item = this.getItem(position)
    if (item != null) {
      (holder as? ViewHolder)?.bind(item)
    }
  }
}
