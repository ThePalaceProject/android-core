package org.nypl.simplified.ui.catalog

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.common.base.Preconditions
import com.google.common.util.concurrent.FluentFuture
import org.joda.time.DateTime
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedEntry.FeedEntryCorrupt
import org.nypl.simplified.feeds.api.FeedEntry.FeedEntryOPDS
import org.nypl.simplified.futures.FluentFutureExtensions.map
import org.nypl.simplified.opds.core.OPDSAvailabilityHeld
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.OPDSAvailabilityMatcherType
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSAvailabilityRevoked
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType

/**
 * A view holder for a single cell in an infinitely-scrolling feed.
 */

class CatalogPagedViewHolder(
  private val context: Context,
  private val listener: CatalogPagedViewListener,
  private val parent: View,
  private val buttonCreator: CatalogButtons,
  private val bookCovers: BookCoverProviderType,
  private val profilesController: ProfilesControllerType
) : RecyclerView.ViewHolder(parent) {

  private var thumbnailLoading: FluentFuture<Unit>? = null

  private val idle =
    this.parent.findViewById<ViewGroup>(R.id.bookCellIdle)!!
  private val corrupt =
    this.parent.findViewById<ViewGroup>(R.id.bookCellCorrupt)!!
  private val error =
    this.parent.findViewById<ViewGroup>(R.id.bookCellError)!!
  private val progress =
    this.parent.findViewById<ViewGroup>(R.id.bookCellInProgress)!!

  private val idleCover =
    this.parent.findViewById<ImageView>(R.id.bookCellIdleCover)!!
  private val idleProgress =
    this.parent.findViewById<ProgressBar>(R.id.bookCellIdleCoverProgress)!!
  private val idleTitle =
    this.idle.findViewById<TextView>(R.id.bookCellIdleTitle)!!
  private val idleMeta =
    this.idle.findViewById<TextView>(R.id.bookCellIdleMeta)!!
  private val idleAuthor =
    this.idle.findViewById<TextView>(R.id.bookCellIdleAuthor)!!
  private val idleButtons =
    this.idle.findViewById<ViewGroup>(R.id.bookCellIdleButtons)!!

  private val progressProgress =
    this.parent.findViewById<ProgressBar>(R.id.bookCellInProgressBar)!!
  private val progressText =
    this.parent.findViewById<TextView>(R.id.bookCellInProgressTitle)!!

  private val errorTitle =
    this.error.findViewById<TextView>(R.id.bookCellErrorTitle)
  private val errorDismiss =
    this.error.findViewById<Button>(R.id.bookCellErrorButtonDismiss)
  private val errorDetails =
    this.error.findViewById<Button>(R.id.bookCellErrorButtonDetails)
  private val errorRetry =
    this.error.findViewById<Button>(R.id.bookCellErrorButtonRetry)

  private var feedEntry: FeedEntry? = null

  fun bindTo(item: FeedEntry?) {
    this.feedEntry = item

    return when (item) {
      is FeedEntryCorrupt -> {
        this.setVisibilityIfNecessary(this.corrupt, View.VISIBLE)
        this.checkSomethingIsVisible()
      }

      is FeedEntryOPDS -> {
        this.listener.registerObserver(item, this::onBookChanged)
        this.checkSomethingIsVisible()
      }

      null -> {
        this.unbind()
        this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
        this.setVisibilityIfNecessary(this.idleProgress, View.VISIBLE)
        this.checkSomethingIsVisible()
      }
    }
  }

  private fun setVisibilityIfNecessary(
    view: View,
    visibility: Int
  ) {
    if (view.visibility != visibility) {
      view.visibility = visibility
    }
  }

  private fun onFeedEntryOPDS(item: FeedEntryOPDS) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.setVisibilityIfNecessary(this.idleCover, View.INVISIBLE)
    this.idleCover.setImageDrawable(null)
    this.idleCover.contentDescription =
      CatalogBookAccessibilityStrings.coverDescription(this.context.resources, item)

    this.setVisibilityIfNecessary(this.idleProgress, View.VISIBLE)
    this.idleTitle.text = item.feedEntry.title
    this.idleAuthor.text = item.feedEntry.authorsCommaSeparated
    this.errorTitle.text = item.feedEntry.title

    this.idleMeta.text = when (item.probableFormat) {
      BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB ->
        context.getString(R.string.catalogBookFormatEPUB)
      BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO ->
        context.getString(R.string.catalogBookFormatAudioBook)
      BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF ->
        context.getString(R.string.catalogBookFormatPDF)
      null -> ""
    }

    val targetHeight =
      this.parent.resources.getDimensionPixelSize(R.dimen.cover_thumbnail_height)
    val targetWidth = 0
    this.thumbnailLoading =
      this.bookCovers.loadThumbnailInto(
        item, this.idleCover, targetWidth, targetHeight
      ).map {
        this.setVisibilityIfNecessary(this.idleProgress, View.INVISIBLE)
        this.setVisibilityIfNecessary(this.idleCover, View.VISIBLE)
      }

    val onClick: (View) -> Unit = { this.listener.openBookDetail(item) }
    this.idle.setOnClickListener(onClick)
    this.idleTitle.setOnClickListener(onClick)
    this.idleCover.setOnClickListener(onClick)
  }

  private fun onBookChanged(bookWithStatus: BookWithStatus) {
    this.onFeedEntryOPDS(this.feedEntry as FeedEntryOPDS)
    this.onBookWithStatus(bookWithStatus)
    this.checkSomethingIsVisible()
  }

  private fun checkSomethingIsVisible() {
    Preconditions.checkState(
      this.idle.visibility == View.VISIBLE ||
        this.progress.visibility == View.VISIBLE ||
        this.corrupt.visibility == View.VISIBLE ||
        this.error.visibility == View.VISIBLE,
      "Something must be visible!"
    )
  }

  private fun onBookWithStatus(book: BookWithStatus) {
    return when (val status = book.status) {
      is BookStatus.Held.HeldInQueue ->
        this.onBookStatusHeldInQueue(status, book.book)
      is BookStatus.Held.HeldReady ->
        this.onBookStatusHeldReady(status, book.book)
      is BookStatus.Holdable ->
        this.onBookStatusHoldable(book.book)
      is BookStatus.Loanable ->
        this.onBookStatusLoanable(book.book)
      is BookStatus.Loaned.LoanedNotDownloaded ->
        this.onBookStatusLoanedNotDownloaded(status, book.book)
      is BookStatus.Loaned.LoanedDownloaded ->
        this.onBookStatusLoanedDownloaded(status, book.book)
      is BookStatus.Revoked ->
        this.onBookStatusRevoked(book)
      is BookStatus.ReachedLoanLimit ->
        this.onBookStatusReachedLoanLimit()
      is BookStatus.FailedRevoke ->
        this.onBookStatusFailedRevoke(status, book.book)
      is BookStatus.FailedDownload ->
        this.onBookStatusFailedDownload(status, book.book)
      is BookStatus.FailedLoan ->
        this.onBookStatusFailedLoan(status, book.book)

      is BookStatus.RequestingRevoke,
      is BookStatus.RequestingLoan,
      is BookStatus.RequestingDownload -> {
        this.setVisibilityIfNecessary(this.corrupt, View.GONE)
        this.setVisibilityIfNecessary(this.error, View.GONE)
        this.setVisibilityIfNecessary(this.idle, View.GONE)
        this.setVisibilityIfNecessary(this.progress, View.VISIBLE)

        this.progressText.text = book.book.entry.title
        this.progressProgress.isIndeterminate = true
      }

      is BookStatus.Downloading ->
        this.onBookStatusDownloading(book, status)
      is BookStatus.DownloadWaitingForExternalAuthentication ->
        this.onBookStatusDownloadWaitingForExternalAuthentication(book.book)
      is BookStatus.DownloadExternalAuthenticationInProgress ->
        this.onBookStatusDownloadExternalAuthenticationInProgress(book.book)
    }
  }

  private fun onBookStatusFailedRevoke(
    bookStatus: BookStatus.FailedRevoke,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.VISIBLE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.errorDismiss.setOnClickListener {
      this.listener.dismissRevokeError(this.feedEntry as FeedEntryOPDS)
    }
    this.errorDetails.setOnClickListener {
      this.listener.showTaskError(book, bookStatus.result)
    }
    this.errorRetry.setOnClickListener {
      this.listener.revokeMaybeAuthenticated(book)
    }
  }

  private fun onBookStatusFailedDownload(
    bookStatus: BookStatus.FailedDownload,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.VISIBLE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.errorDismiss.setOnClickListener {
      this.listener.dismissBorrowError(this.feedEntry as FeedEntryOPDS)
    }
    this.errorDetails.setOnClickListener {
      this.listener.showTaskError(book, bookStatus.result)
    }
    this.errorRetry.setOnClickListener {
      this.listener.borrowMaybeAuthenticated(book)
    }
  }

  private fun onBookStatusFailedLoan(
    bookStatus: BookStatus.FailedLoan,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.VISIBLE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.errorDismiss.setOnClickListener {
      this.listener.dismissBorrowError(this.feedEntry as FeedEntryOPDS)
    }
    this.errorDetails.setOnClickListener {
      this.listener.showTaskError(book, bookStatus.result)
    }
    this.errorRetry.setOnClickListener {
      this.listener.borrowMaybeAuthenticated(book)
    }
  }

  private fun onBookStatusLoanedNotDownloaded(
    bookStatus: BookStatus.Loaned.LoanedNotDownloaded,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.idleButtons.removeAllViews()

    val loanDuration = getLoanDuration(book)

    this.idleButtons.addView(
      when {
        loanDuration.isNotEmpty() -> {
          this.buttonCreator.createDownloadButtonWithLoanDuration(loanDuration) {
            this.listener.borrowMaybeAuthenticated(book)
          }
        }
        bookStatus.isOpenAccess -> {
          this.buttonCreator.createGetButton(
            onClick = {
              this.listener.borrowMaybeAuthenticated(book)
            },
            heightMatchParent = true
          )
        }
        else -> {
          this.buttonCreator.createDownloadButton(
            onClick = {
              this.listener.borrowMaybeAuthenticated(book)
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
            this.listener.revokeMaybeAuthenticated(book)
          },
          heightMatchParent = true
        )
      )
    } else if (isBookDeletable(book)) {
      this.idleButtons.addView(this.buttonCreator.createButtonSpace())
      this.idleButtons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = {
            this.listener.delete(this.feedEntry as FeedEntryOPDS)
          },
          heightMatchParent = true
        )
      )
    }
  }

  private fun onBookStatusLoanable(book: Book) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.idleButtons.removeAllViews()
    this.idleButtons.addView(
      this.buttonCreator.createGetButton(
        onClick = {
          this.listener.borrowMaybeAuthenticated(book)
        }
      )
    )
  }

  private fun onBookStatusReachedLoanLimit() {
    AlertDialog.Builder(context)
      .setTitle(R.string.bookReachedLoanLimitDialogTitle)
      .setMessage(R.string.bookReachedLoanLimitDialogMessage)
      .setPositiveButton(R.string.bookReachedLoanLimitDialogButton) { dialog, _ ->
        dialog.dismiss()
      }
      .create()
      .show()

    this.listener.resetInitialBookStatus(this.feedEntry as FeedEntryOPDS)
  }

  private fun onBookStatusHoldable(book: Book) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.idleButtons.removeAllViews()
    this.idleButtons.addView(
      this.buttonCreator.createReserveButton(
        onClick = {
          this.listener.reserveMaybeAuthenticated(book)
        }
      )
    )
  }

  private fun onBookStatusHeldReady(
    status: BookStatus.Held.HeldReady,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.idleButtons.removeAllViews()
    this.idleButtons.addView(
      this.buttonCreator.createGetButton(
        onClick = {
          this.listener.borrowMaybeAuthenticated(book)
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
            this.listener.revokeMaybeAuthenticated(book)
          }
        )
      )
    }
  }

  private fun onBookStatusHeldInQueue(
    status: BookStatus.Held.HeldInQueue,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.idleButtons.removeAllViews()
    if (status.isRevocable) {
      this.idleButtons.addView(
        this.buttonCreator.createRevokeHoldButton(
          onClick = {
            this.listener.revokeMaybeAuthenticated(book)
          }
        )
      )
    } else {
      this.idleButtons.addView(
        this.buttonCreator.createCenteredTextForButtons(R.string.catalogHoldCannotCancel)
      )
    }
  }

  private fun onBookStatusLoanedDownloaded(
    bookStatus: BookStatus.Loaned.LoanedDownloaded,
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.idleButtons.removeAllViews()

    when (val format = book.findPreferredFormat()) {
      is BookFormat.BookFormatPDF,
      is BookFormat.BookFormatEPUB -> {
        val loanDuration = getLoanDuration(book)
        this.idleButtons.addView(
          if (loanDuration.isNotEmpty()) {
            this.buttonCreator.createReadButtonWithLoanDuration(loanDuration) {
              this.listener.openViewer(book, format)
            }
          } else {
            this.buttonCreator.createReadButton(
              onClick = {
                this.listener.openViewer(book, format)
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
              this.listener.openViewer(book, format)
            }
          } else {
            this.buttonCreator.createListenButton(
              onClick = {
                this.listener.openViewer(book, format)
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
            this.listener.revokeMaybeAuthenticated(book)
          },
          heightMatchParent = true
        )
      )
    } else if (isBookDeletable(book)) {
      this.idleButtons.addView(this.buttonCreator.createButtonSpace())
      this.idleButtons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = {
            this.listener.delete(this.feedEntry as FeedEntryOPDS)
          },
          heightMatchParent = true
        )
      )
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun onBookStatusRevoked(book: BookWithStatus) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.VISIBLE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

    this.idleButtons.removeAllViews()
  }

  private fun onBookStatusDownloading(
    book: BookWithStatus,
    status: BookStatus.Downloading
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.VISIBLE)

    this.progressText.text = book.book.entry.title

    val progressPercent = status.progressPercent?.toInt()
    if (progressPercent != null) {
      this.progressProgress.isIndeterminate = false
      this.progressProgress.progress = progressPercent
    } else {
      this.progressProgress.isIndeterminate = true
    }
  }

  private fun onBookStatusDownloadWaitingForExternalAuthentication(
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.VISIBLE)

    this.progressText.text = book.entry.title
    this.progressProgress.isIndeterminate = true
  }

  private fun onBookStatusDownloadExternalAuthenticationInProgress(
    book: Book
  ) {
    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.VISIBLE)

    this.progressText.text = book.entry.title
    this.progressProgress.isIndeterminate = true
  }

  fun unbind() {
    val currentFeedEntry = this.feedEntry
    if (currentFeedEntry is FeedEntryOPDS) {
      this.listener.unregisterObserver(currentFeedEntry, this::onBookChanged)
    }

    this.setVisibilityIfNecessary(this.corrupt, View.GONE)
    this.setVisibilityIfNecessary(this.error, View.GONE)
    this.setVisibilityIfNecessary(this.idle, View.GONE)
    this.setVisibilityIfNecessary(this.progress, View.GONE)

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

    this.thumbnailLoading = this.thumbnailLoading?.let { loading ->
      loading.cancel(true)
      null
    }
  }

  private fun getLoanDuration(book: Book): String {
    val status = BookStatus.fromBook(book)
    return if (status is BookStatus.Loaned.LoanedDownloaded ||
      status is BookStatus.Loaned.LoanedNotDownloaded
    ) {
      val endDate = (status as? BookStatus.Loaned.LoanedDownloaded)?.loanExpiryDate
        ?: (status as? BookStatus.Loaned.LoanedNotDownloaded)?.loanExpiryDate

      if (
        endDate != null
      ) {
        CatalogBookAvailabilityStrings.intervalStringLoanDuration(
          this.context.resources,
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

  private fun isBookReturnable(book: Book): Boolean {
    val profile = this.profilesController.profileCurrent()
    val account = profile.account(book.account)

    return try {
      if (account.bookDatabase.books().contains(book.id)) {
        when (val status = BookStatus.fromBook(book)) {
          is BookStatus.Loaned.LoanedDownloaded ->
            status.returnable
          is BookStatus.Loaned.LoanedNotDownloaded ->
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

  private fun isBookDeletable(book: Book): Boolean {
    return try {
      val profile = this.profilesController.profileCurrent()
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
