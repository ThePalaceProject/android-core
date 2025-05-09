package org.nypl.simplified.ui.catalog

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.io7m.jfunctional.Some
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.format.DateTimeFormatterBuilder
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookFormat.BookFormatAudioBook
import org.nypl.simplified.books.api.BookFormat.BookFormatEPUB
import org.nypl.simplified.books.api.BookFormat.BookFormatPDF
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatus.DownloadExternalAuthenticationInProgress
import org.nypl.simplified.books.book_registry.BookStatus.DownloadWaitingForExternalAuthentication
import org.nypl.simplified.books.book_registry.BookStatus.Downloading
import org.nypl.simplified.books.book_registry.BookStatus.FailedDownload
import org.nypl.simplified.books.book_registry.BookStatus.FailedLoan
import org.nypl.simplified.books.book_registry.BookStatus.FailedRevoke
import org.nypl.simplified.books.book_registry.BookStatus.Held
import org.nypl.simplified.books.book_registry.BookStatus.Held.HeldInQueue
import org.nypl.simplified.books.book_registry.BookStatus.Held.HeldReady
import org.nypl.simplified.books.book_registry.BookStatus.Holdable
import org.nypl.simplified.books.book_registry.BookStatus.Loanable
import org.nypl.simplified.books.book_registry.BookStatus.Loaned
import org.nypl.simplified.books.book_registry.BookStatus.Loaned.LoanedDownloaded
import org.nypl.simplified.books.book_registry.BookStatus.Loaned.LoanedNotDownloaded
import org.nypl.simplified.books.book_registry.BookStatus.ReachedLoanLimit
import org.nypl.simplified.books.book_registry.BookStatus.RequestingDownload
import org.nypl.simplified.books.book_registry.BookStatus.RequestingLoan
import org.nypl.simplified.books.book_registry.BookStatus.RequestingRevoke
import org.nypl.simplified.books.book_registry.BookStatus.Revoked
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.Feed.FeedWithGroups
import org.nypl.simplified.feeds.api.Feed.FeedWithoutGroups
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderSuccess
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import java.net.URI

class CatalogFeedViewDetails(
  override val root: ViewGroup,
  private val buttonCreator: CatalogButtons,
  private val covers: BookCoverProviderType,
  private val layoutInflater: LayoutInflater,
  private val onShowErrorDetails: (TaskResult.Failure<*>) -> Unit,
  private val onBookDismissError: (CatalogBookStatus<*>) -> Unit,
  private val onBookSAMLDownloadRequested: (CatalogBookStatus<DownloadWaitingForExternalAuthentication>) -> Unit,
  private val onBookBorrowRequested: (CatalogBorrowParameters) -> Unit,
  private val onBookBorrowCancelRequested: (CatalogBookStatus<*>) -> Unit,
  private val onBookCanBeDeleted: (CatalogBookStatus<*>) -> Boolean,
  private val onBookCanBeRevoked: (CatalogBookStatus<*>) -> Boolean,
  private val onBookDeleteRequested: (CatalogBookStatus<*>) -> Unit,
  private val onBookPreviewOpenRequested: (CatalogBookStatus<*>) -> Unit,
  private val onBookReserveRequested: (CatalogBorrowParameters) -> Unit,
  private val onBookResetStatusInitial: (CatalogBookStatus<*>) -> Unit,
  private val onBookRevokeRequested: (CatalogBookStatus<*>) -> Unit,
  private val onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit,
  private val onBookViewerOpen: (Book, BookFormat) -> Unit,
  private val onFeedSelected: (accountID: AccountID, title: String, uri: URI) -> Unit,
  private val onToolbarBackPressed: () -> Unit,
  private val onToolbarLogoPressed: () -> Unit,
  private val screenSize: ScreenSizeInformationType,
  private val window: Window,
) : CatalogFeedView() {

  private val genreUriScheme =
    "http://librarysimplified.org/terms/genres/Simplified/"

  private val dateFormatter =
    DateTimeFormatterBuilder()
      .appendYear(4, 5)
      .appendLiteral('-')
      .appendMonthOfYear(2)
      .appendLiteral('-')
      .appendDayOfMonth(2)
      .toFormatter()

  private val dateTimeFormatter =
    DateTimeFormatterBuilder()
      .appendYear(4, 5)
      .appendLiteral('-')
      .appendMonthOfYear(2)
      .appendLiteral('-')
      .appendDayOfMonth(2)
      .appendLiteral(' ')
      .appendHourOfDay(2)
      .appendLiteral(':')
      .appendMinuteOfHour(2)
      .appendLiteral(':')
      .appendSecondOfMinute(2)
      .toFormatter()

  private val title: TextView =
    this.root.findViewById(R.id.bookDetailTitle)
  private val authors: TextView =
    this.root.findViewById(R.id.bookDetailAuthors)
  private val buttons: LinearLayout =
    this.root.findViewById(R.id.bookDetailButtons)
  private val cover: ImageView =
    this.root.findViewById(R.id.bookDetailCoverImage)
  private val description: TextView =
    this.root.findViewById(R.id.bookDetailDescriptionText)
  private val seeMore: TextView =
    this.root.findViewById(R.id.seeMoreText)
  private val metadata: LinearLayout =
    this.root.findViewById(R.id.bookDetailMetadataTable)

  private val statusIdle: ViewGroup =
    this.root.findViewById(R.id.bookDetailStatusIdle)
  private val statusFailed: ViewGroup =
    this.root.findViewById(R.id.bookDetailStatusFailed)
  private val statusInProgress: ViewGroup =
    this.root.findViewById(R.id.bookDetailStatusInProgress)

  private val statusIdleText: TextView =
    this.statusIdle.findViewById(R.id.idleText)

  private val statusInProgressText: TextView =
    this.statusInProgress.findViewById(R.id.inProgressText)
  private val statusInProgressBar: ProgressBar =
    this.statusInProgress.findViewById(R.id.inProgressBar)

  private val relatedLayout: FrameLayout =
    this.root.findViewById(R.id.bookDetailRelatedBooksContainer)
  private val relatedListView: RecyclerView =
    this.root.findViewById(R.id.relatedBooksList)
  private val relatedLoading: ProgressBar =
    this.root.findViewById(R.id.feedLoadProgress)
  private val relatedAdapter =
    CatalogFeedWithGroupsAdapter(
      covers = this.covers,
      onFeedSelected = this.onFeedSelected,
      onBookSelected = this.onBookSelected
    )

  val toolbar: CatalogToolbar =
    CatalogToolbar(
      logo = this.root.findViewById(R.id.catalogDetailToolbarLogo),
      logoTouch = this.root.findViewById(R.id.catalogDetailToolbarLogoTouch),
      onToolbarBackPressed = this.onToolbarBackPressed,
      onToolbarLogoPressed = this.onToolbarLogoPressed,
      onSearchSubmitted = { _, _, _ -> },
      searchIcon = this.root.findViewById(R.id.catalogDetailToolbarSearchIcon),
      searchText = this.root.findViewById(R.id.catalogDetailToolbarSearchText),
      searchTouch = this.root.findViewById(R.id.catalogDetailToolbarSearchIconTouch),
      text = this.root.findViewById(R.id.catalogDetailToolbarText),
      window = this.window
    )

  init {
    this.relatedListView.layoutManager = LinearLayoutManager(this.root.context)
    this.relatedListView.setHasFixedSize(true)
    (this.relatedListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    this.relatedListView.setItemViewCacheSize(8)
    this.relatedListView.addItemDecoration(
      CatalogFeedWithGroupsDecorator(this.screenSize.dpToPixels(16).toInt())
    )
    this.relatedListView.adapter = this.relatedAdapter
  }

  private fun configureDescription(
    newEntry: FeedEntry.FeedEntryOPDS
  ) {
    val opds = newEntry.feedEntry

    /*
     * Render the HTML present in the summary and insert it into the text view.
     */

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
      this.description.text = Html.fromHtml(opds.summary, Html.FROM_HTML_MODE_LEGACY)
    } else {
      @Suppress("DEPRECATION")
      this.description.text = Html.fromHtml(opds.summary)
    }

    this.description.post {
      this.seeMore.visibility = if (this.description.lineCount > 6) {
        this.description.maxLines = 6
        this.seeMore.setOnClickListener {
          this.description.maxLines = Integer.MAX_VALUE
          this.seeMore.visibility = View.GONE
        }
        View.VISIBLE
      } else {
        View.GONE
      }
    }
  }

  private fun formatDuration(
    seconds: Double
  ): String {
    val duration =
      Duration.standardSeconds(seconds.toLong())
    val hours =
      Duration.standardHours(duration.standardHours)
    val remaining = duration.minus(hours)

    return this.root.resources.getString(
      R.string.catalogDurationFormat,
      hours.standardHours.toString(),
      remaining.standardMinutes.toString()
    )
  }

  private fun bookInfoViewOf(): Triple<View, TextView, TextView> {
    val row = this.layoutInflater.inflate(R.layout.book_detail_metadata_item, this.metadata, false)
    val rowKey = row.findViewById<TextView>(R.id.key)
    val rowVal = row.findViewById<TextView>(R.id.value)
    return Triple(row, rowKey, rowVal)
  }

  private fun configureMetadataTable(
    newEntry: FeedEntry.FeedEntryOPDS
  ) {
    this.metadata.removeAllViews()

    val probableFormat =
      newEntry.probableFormat

    val bookFormatText = when (probableFormat) {
      BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB ->
        this.root.resources.getString(R.string.catalogBookFormatEPUB)

      BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO ->
        this.root.resources.getString(R.string.catalogBookFormatAudioBook)

      BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF ->
        this.root.resources.getString(R.string.catalogBookFormatPDF)

      else -> {
        ""
      }
    }

    if (bookFormatText.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.root.resources.getString(R.string.catalogMetaFormat)
      rowVal.text = bookFormatText
      this.metadata.addView(row)
    }

    val entry = newEntry.feedEntry
    val publishedOpt = entry.published
    if (publishedOpt is Some<DateTime>) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.root.resources.getString(R.string.catalogMetaPublicationDate)
      rowVal.text = this.dateFormatter.print(publishedOpt.get())
      this.metadata.addView(row)
    }

    val publisherOpt = entry.publisher
    if (publisherOpt is Some<String>) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.root.resources.getString(R.string.catalogMetaPublisher)
      rowVal.text = publisherOpt.get()
      this.metadata.addView(row)
    }

    if (entry.distribution.isNotBlank()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.root.resources.getString(R.string.catalogMetaDistributor)
      rowVal.text = entry.distribution
      this.metadata.addView(row)
    }

    val categories =
      entry.categories.filter { opdsCategory -> opdsCategory.scheme == this.genreUriScheme }
    if (categories.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.root.resources.getString(R.string.catalogMetaCategories)
      rowVal.text = categories.joinToString(", ") { opdsCategory -> opdsCategory.effectiveLabel }
      this.metadata.addView(row)
    }

    val narrators = entry.narrators.filterNot { it.isBlank() }
    if (narrators.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.root.resources.getString(R.string.catalogMetaNarrators)
      rowVal.text = narrators.joinToString(", ")
      this.metadata.addView(row)
    }

    this.run {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.root.resources.getString(R.string.catalogMetaUpdatedDate)
      rowVal.text = this.dateTimeFormatter.print(entry.updated)
      this.metadata.addView(row)
    }

    val duration = entry.duration
    if (duration.isSome) {
      val durationValue = (duration as Some<Double>).get()
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.root.resources.getString(R.string.catalogMetaDuration)
      rowVal.text = formatDuration(durationValue)
      this.metadata.addView(row)
    }
  }

  fun bind(
    newEntry: FeedEntry.FeedEntryOPDS
  ) {
    this.relatedLoading.visibility =
      View.VISIBLE
    this.statusFailed.visibility =
      View.INVISIBLE
    this.statusInProgress.visibility =
      View.INVISIBLE
    this.statusIdle.visibility =
      View.VISIBLE

    this.title.text =
      newEntry.feedEntry.title
    this.authors.text =
      newEntry.feedEntry.authorsCommaSeparated

    this.configureDescription(newEntry)
    this.configureMetadataTable(newEntry)

    val targetHeight =
      this.root.resources.getDimensionPixelSize(
        org.librarysimplified.books.covers.R.dimen.cover_detail_height
      )

    this.covers.loadCoverInto(
      entry = newEntry,
      imageView = this.cover,
      hasBadge = true,
      width = 0,
      height = targetHeight
    )
  }

  fun setNoRelatedFeed() {
    this.relatedLayout.visibility = View.GONE
  }

  fun bindRelatedFeedResult(
    feedResult: FeedLoaderResult
  ) {
    when (feedResult) {
      is FeedLoaderFailedAuthentication -> {
        this.relatedLayout.visibility = View.GONE
      }

      is FeedLoaderFailedGeneral -> {
        this.relatedLayout.visibility = View.GONE
      }

      is FeedLoaderSuccess -> {
        when (val feed = feedResult.feed) {
          is FeedWithGroups -> {
            this.relatedAdapter.submitList(feed.feedGroupsInOrder)
            this.relatedLayout.visibility = View.VISIBLE
            this.relatedLoading.visibility = View.INVISIBLE
            this.relatedListView.visibility = View.VISIBLE
          }

          is FeedWithoutGroups -> {
            this.relatedLayout.visibility = View.GONE
          }
        }
      }
    }
  }

  fun <S : BookStatus> onStatusUpdate(
    status: CatalogBookStatus<S>
  ) {
    UIThread.checkIsUIThread()

    when (status.status) {
      is Held -> {
        this.onStatusHeld(status as CatalogBookStatus<Held>)
      }

      is DownloadExternalAuthenticationInProgress -> {
        this.onStatusDownloadExternalAuthenticationInProgress(
          status as CatalogBookStatus<DownloadExternalAuthenticationInProgress>)
      }

      is DownloadWaitingForExternalAuthentication -> {
        this.onStatusDownloadWaitingForExternalAuthentication(
          status as CatalogBookStatus<DownloadWaitingForExternalAuthentication>
        )
      }

      is Downloading -> {
        this.onStatusDownloading(status as CatalogBookStatus<Downloading>)
      }

      is FailedDownload -> {
        this.onStatusFailedDownload(status as CatalogBookStatus<FailedDownload>)
      }

      is FailedLoan -> {
        this.onStatusFailedLoan(status as CatalogBookStatus<FailedLoan>)
      }

      is FailedRevoke -> {
        this.onStatusFailedRevoke(status as CatalogBookStatus<FailedRevoke>)
      }

      is Holdable -> {
        this.onStatusHoldable(status as CatalogBookStatus<Holdable>)
      }

      is Loanable -> {
        this.onStatusLoanable(status as CatalogBookStatus<Loanable>)
      }

      is Loaned -> {
        this.onStatusLoaned(status as CatalogBookStatus<Loaned>)
      }

      is ReachedLoanLimit -> {
        this.onStatusReachedLoanLimit(status as CatalogBookStatus<ReachedLoanLimit>)
      }

      is RequestingDownload -> {
        this.onStatusRequestingDownload()
      }

      is RequestingLoan -> {
        this.onStatusRequestingLoan()
      }

      is RequestingRevoke -> {
        this.onStatusRequestingRevoke()
      }

      is Revoked -> {
        this.onStatusRevoked(status as CatalogBookStatus<Revoked>)
      }
    }
  }

  private fun onStatusLoaned(
    status: CatalogBookStatus<Loaned>
  ) {
    this.buttons.removeAllViews()

    when (status.status) {
      is LoanedNotDownloaded -> {
        this.buttons.addView(
          if (status.status.isOpenAccess) {
            this.buttonCreator.createGetButton(
              onClick = {
                this.onBookBorrowRequested(
                  CatalogBorrowParameters(
                    accountID = status.book.account,
                    bookID = status.book.id,
                    entry = status.book.entry,
                    samlDownloadContext = null
                  )
                )
              }
            )
          } else {
            this.buttonCreator.createDownloadButton(
              onClick = {
                this.onBookBorrowRequested(
                  CatalogBorrowParameters(
                    accountID = status.book.account,
                    bookID = status.book.id,
                    entry = status.book.entry,
                    samlDownloadContext = null
                  )
                )
              }
            )
          }
        )
      }

      is LoanedDownloaded -> {
        when (val format = status.book.findPreferredFormat()) {
          is BookFormatPDF,
          is BookFormatEPUB -> {
            this.buttons.addView(
              this.buttonCreator.createReadButton(
                onClick = { this.onBookViewerOpen(status.book, format) }
              )
            )
          }

          is BookFormatAudioBook -> {
            this.buttons.addView(
              this.buttonCreator.createListenButton(
                onClick = { this.onBookViewerOpen(status.book, format) }
              )
            )
          }

          else -> {
            // do nothing
          }
        }
      }
    }

    val isRevocable = this.onBookCanBeRevoked(status)
    if (isRevocable) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = { this.onBookRevokeRequested(status) }
        )
      )
    } else if (this.onBookCanBeDeleted(status)) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = { this.onBookDeleteRequested(status) }
        )
      )
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace(), 0)
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }

    this.statusInProgress.visibility =
      View.INVISIBLE
    this.statusIdle.visibility =
      View.VISIBLE
    this.statusFailed.visibility =
      View.INVISIBLE
    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.root.resources, status.status)
  }

  private fun onStatusLoanable(
    status: CatalogBookStatus<Loanable>
  ) {
    this.buttons.removeAllViews()

    val createPreviewButton =
      status.previewStatus != BookPreviewStatus.None

    if (createPreviewButton) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }

    this.buttons.addView(
      this.buttonCreator.createGetButton(
        onClick = {
          this.onBookBorrowRequested(
            CatalogBorrowParameters(
              accountID = status.book.account,
              bookID = status.book.id,
              entry = status.book.entry,
              samlDownloadContext = null
            )
          )
        }
      )
    )

    if (createPreviewButton) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createReadPreviewButton(
          bookFormat = BookFormats.inferFormat(status.book.entry),
          onClick = { this.onBookPreviewOpenRequested(status) }
        )
      )
      this.buttons.addView(this.buttonCreator.createButtonSpace())
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }

    this.statusInProgress.visibility =
      View.INVISIBLE
    this.statusIdle.visibility =
      View.VISIBLE
    this.statusFailed.visibility =
      View.INVISIBLE

    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.root.resources, status.status)
  }

  private fun onStatusHoldable(
    status: CatalogBookStatus<Holdable>
  ) {
    this.buttons.removeAllViews()

    val createPreviewButton =
      status.previewStatus != BookPreviewStatus.None

    if (createPreviewButton) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }

    this.buttons.addView(
      this.buttonCreator.createReserveButton(
        onClick = {
          this.onBookReserveRequested(
            CatalogBorrowParameters(
              accountID = status.book.account,
              bookID = status.book.id,
              entry = status.book.entry,
              samlDownloadContext = null
            )
          )
        }
      )
    )

    if (createPreviewButton) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createReadPreviewButton(
          bookFormat = BookFormats.inferFormat(status.book.entry),
          onClick = { this.onBookPreviewOpenRequested(status) }
        )
      )
      this.buttons.addView(this.buttonCreator.createButtonSpace())
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }

    this.statusInProgress.visibility =
      View.INVISIBLE
    this.statusIdle.visibility =
      View.VISIBLE
    this.statusFailed.visibility =
      View.INVISIBLE

    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.root.resources, status.status)
  }

  private fun onStatusReachedLoanLimit(
    status: CatalogBookStatus<ReachedLoanLimit>
  ) {
    MaterialAlertDialogBuilder(this.root.context)
      .setTitle(R.string.bookReachedLoanLimitDialogTitle)
      .setMessage(R.string.bookReachedLoanLimitDialogMessage)
      .setPositiveButton(R.string.bookReachedLoanLimitDialogButton) { dialog, _ ->
        try {
          this.onBookResetStatusInitial(status)
        } catch (e: Throwable) {
          // Don't care!
        }
        dialog.dismiss()
      }
      .create()
      .show()
  }

  private fun onStatusRequestingDownload() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onStatusRequestingLoan() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onStatusRequestingRevoke() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onStatusRevoked(
    status: CatalogBookStatus<Revoked>
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.root.resources, status.status)
  }

  private fun onStatusHeld(
    status: CatalogBookStatus<Held>
  ) {
    this.buttons.removeAllViews()

    val createPreviewButton =
      status.previewStatus != BookPreviewStatus.None

    when (status.status) {
      is HeldInQueue -> {
        if (createPreviewButton) {
          this.buttons.addView(
            this.buttonCreator.createReadPreviewButton(
              bookFormat = BookFormats.inferFormat(status.book.entry),
              onClick = { this.onBookPreviewOpenRequested(status) }
            )
          )
          this.buttons.addView(this.buttonCreator.createButtonSpace())
        } else {
          this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
        }

        if (status.status.isRevocable) {
          this.buttons.addView(
            this.buttonCreator.createRevokeHoldButton(
              onClick = { this.onBookRevokeRequested(status) }
            )
          )
        } else {
          this.buttons.addView(
            this.buttonCreator.createCenteredTextForButtons(R.string.catalogHoldCannotCancel)
          )
        }

        this.buttons.addView(
          if (createPreviewButton) {
            this.buttonCreator.createButtonSpace()
          } else {
            this.buttonCreator.createButtonSizedSpace()
          }
        )
      }

      is HeldReady -> {
        // if there will be at least one more button
        if (createPreviewButton || status.status.isRevocable) {
          this.buttons.addView(this.buttonCreator.createButtonSpace())
        } else {
          this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
        }

        this.buttons.addView(
          this.buttonCreator.createGetButton(
            onClick = {
              this.onBookBorrowRequested(
                CatalogBorrowParameters(
                  accountID = status.book.account,
                  bookID = status.book.id,
                  entry = status.book.entry,
                  samlDownloadContext = null
                )
              )
            }
          )
        )

        if (createPreviewButton) {
          this.buttons.addView(this.buttonCreator.createButtonSpace())

          this.buttons.addView(
            this.buttonCreator.createReadPreviewButton(
              bookFormat = BookFormats.inferFormat(status.book.entry),
              onClick = { this.onBookPreviewOpenRequested(status) }
            )
          )
        }

        if (status.status.isRevocable) {
          this.buttons.addView(this.buttonCreator.createButtonSpace())

          this.buttons.addView(
            this.buttonCreator.createRevokeHoldButton(
              onClick = { this.onBookRevokeRequested(status) }
            )
          )

          this.buttons.addView(this.buttonCreator.createButtonSpace())
        } else if (!createPreviewButton) {
          // if the book is not revocable and there's no preview button, we need to add a dummy
          // button on the right
          this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
        }
      }
    }

    this.statusInProgress.visibility =
      View.INVISIBLE
    this.statusIdle.visibility =
      View.VISIBLE
    this.statusFailed.visibility =
      View.INVISIBLE
    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.root.resources, status.status)
  }

  private fun onStatusFailedRevoke(
    status: CatalogBookStatus<FailedRevoke>
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(
      this.buttonCreator.createDismissButton {
        this.onBookDismissError.invoke(status)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createDetailsButton {
        this.onShowErrorDetails.invoke(status.status.result)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createRetryButton {
        this.onBookRevokeRequested.invoke(status)
      }
    )

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.VISIBLE
  }

  private fun onStatusFailedLoan(
    status: CatalogBookStatus<FailedLoan>
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(
      this.buttonCreator.createDismissButton {
        this.onBookDismissError.invoke(status)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createDetailsButton {
        this.onShowErrorDetails.invoke(status.status.result)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createRetryButton {
        this.onBookBorrowRequested.invoke(
          CatalogBorrowParameters(
            accountID = status.book.account,
            bookID = status.book.id,
            entry = status.book.entry,
            samlDownloadContext = null
          )
        )
      }
    )

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.VISIBLE
  }

  private fun onStatusFailedDownload(
    status: CatalogBookStatus<FailedDownload>
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(
      this.buttonCreator.createDismissButton {
        this.onBookDismissError.invoke(status)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createDetailsButton {
        this.onShowErrorDetails.invoke(status.status.result)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createRetryButton {
        this.onBookBorrowRequested.invoke(
          CatalogBorrowParameters(
            accountID = status.book.account,
            bookID = status.book.id,
            entry = status.book.entry,
            samlDownloadContext = null
          )
        )
      }
    )

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.VISIBLE
  }

  private fun onStatusDownloading(
    status: CatalogBookStatus<Downloading>
  ) {
    this.buttons.removeAllViews()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    val progressPercent = status.status.progressPercent?.toInt()
    if (progressPercent != null) {
      this.statusInProgressText.visibility = View.VISIBLE
      this.statusInProgressText.text = "$progressPercent%"
      this.statusInProgressBar.isIndeterminate = false
      this.statusInProgressBar.progress = progressPercent
    } else {
      this.statusInProgressText.visibility = View.GONE
      this.statusInProgressBar.isIndeterminate = true
    }
  }

  private fun onStatusDownloadWaitingForExternalAuthentication(
    status: CatalogBookStatus<DownloadWaitingForExternalAuthentication>
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    this.buttons.addView(this.buttonCreator.createCancelDownloadButton {
      this.onBookBorrowCancelRequested.invoke(status)
    })
    this.buttons.addView(this.buttonCreator.createButtonSizedSpace())

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusIdleText.setText(R.string.catalogLoginRequired)

    this.onBookSAMLDownloadRequested(status)
  }

  private fun onStatusDownloadExternalAuthenticationInProgress(
    status: CatalogBookStatus<DownloadExternalAuthenticationInProgress>
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    this.buttons.addView(this.buttonCreator.createCancelDownloadButton {
      this.onBookBorrowCancelRequested.invoke(status)
    })
    this.buttons.addView(this.buttonCreator.createButtonSizedSpace())

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusIdleText.setText(R.string.catalogLoginRequired)
  }

  companion object {
    fun create(
      buttonCreator: CatalogButtons,
      container: ViewGroup,
      covers: BookCoverProviderType,
      layoutInflater: LayoutInflater,
      onShowErrorDetails: (TaskResult.Failure<*>) -> Unit,
      onBookSAMLDownloadRequested: (CatalogBookStatus<DownloadWaitingForExternalAuthentication>) -> Unit,
      onBookDismissError: (CatalogBookStatus<*>) -> Unit,
      onBookBorrowRequested: (CatalogBorrowParameters) -> Unit,
      onBookBorrowCancelRequested: (CatalogBookStatus<*>) -> Unit,
      onBookCanBeDeleted: (CatalogBookStatus<*>) -> Boolean,
      onBookCanBeRevoked: (CatalogBookStatus<*>) -> Boolean,
      onBookDeleteRequested: (CatalogBookStatus<*>) -> Unit,
      onBookPreviewOpenRequested: (CatalogBookStatus<*>) -> Unit,
      onBookReserveRequested: (CatalogBorrowParameters) -> Unit,
      onBookResetStatusInitial: (CatalogBookStatus<*>) -> Unit,
      onBookRevokeRequested: (CatalogBookStatus<*>) -> Unit,
      onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit,
      onBookViewerOpen: (Book, BookFormat) -> Unit,
      onFeedSelected: (accountID: AccountID, title: String, uri: URI) -> Unit,
      onToolbarBackPressed: () -> Unit,
      onToolbarLogoPressed: () -> Unit,
      screenSize: ScreenSizeInformationType,
      window: Window,
    ): CatalogFeedViewDetails {
      return CatalogFeedViewDetails(
        window = window,
        buttonCreator = buttonCreator,
        covers = covers,
        layoutInflater = layoutInflater,
        onShowErrorDetails = onShowErrorDetails,
        onBookDismissError = onBookDismissError,
        onBookSAMLDownloadRequested = onBookSAMLDownloadRequested,
        onBookBorrowRequested = onBookBorrowRequested,
        onBookBorrowCancelRequested = onBookBorrowCancelRequested,
        onBookCanBeDeleted = onBookCanBeDeleted,
        onBookCanBeRevoked = onBookCanBeRevoked,
        onBookDeleteRequested = onBookDeleteRequested,
        onBookPreviewOpenRequested = onBookPreviewOpenRequested,
        onBookReserveRequested = onBookReserveRequested,
        onBookResetStatusInitial = onBookResetStatusInitial,
        onBookRevokeRequested = onBookRevokeRequested,
        onBookSelected = onBookSelected,
        onBookViewerOpen = onBookViewerOpen,
        onFeedSelected = onFeedSelected,
        onToolbarBackPressed = onToolbarBackPressed,
        onToolbarLogoPressed = onToolbarLogoPressed,
        root = layoutInflater.inflate(R.layout.book_detail, container, true) as ViewGroup,
        screenSize = screenSize,
      )
    }
  }

  override fun clear() {
    this.root.isEnabled = false
  }
}
