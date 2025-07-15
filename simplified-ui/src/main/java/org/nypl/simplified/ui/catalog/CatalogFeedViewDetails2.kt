package org.nypl.simplified.ui.catalog

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TableLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.core.math.MathUtils.clamp
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_DRAGGING
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_SETTLING
import com.google.android.material.bottomsheet.BottomSheetBehavior.from
import com.google.common.util.concurrent.MoreExecutors
import com.io7m.jfunctional.Some
import org.joda.time.DateTime
import org.joda.time.Days
import org.joda.time.Duration
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatterBuilder
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookStatus.DownloadExternalAuthenticationInProgress
import org.nypl.simplified.books.book_registry.BookStatus.DownloadWaitingForExternalAuthentication
import org.nypl.simplified.books.book_registry.BookStatus.Downloading
import org.nypl.simplified.books.book_registry.BookStatus.FailedDownload
import org.nypl.simplified.books.book_registry.BookStatus.FailedLoan
import org.nypl.simplified.books.book_registry.BookStatus.FailedRevoke
import org.nypl.simplified.books.book_registry.BookStatus.Held.HeldInQueue
import org.nypl.simplified.books.book_registry.BookStatus.Held.HeldReady
import org.nypl.simplified.books.book_registry.BookStatus.Holdable
import org.nypl.simplified.books.book_registry.BookStatus.Loanable
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
import org.nypl.simplified.ui.catalog.CatalogFeedViewDetails2.InfoState.BORROWING
import org.nypl.simplified.ui.catalog.CatalogFeedViewDetails2.InfoState.BORROWING_AND_PROGRESS
import org.nypl.simplified.ui.catalog.CatalogFeedViewDetails2.InfoState.GENERIC
import org.nypl.simplified.ui.catalog.CatalogFeedViewDetails2.InfoState.NONE
import org.nypl.simplified.ui.catalog.CatalogFeedViewDetails2.InfoState.PROGRESS
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.Locale

class CatalogFeedViewDetails2(
  override val root: ViewGroup,
  private val screenSize: ScreenSizeInformationType,
  private val layoutInflater: LayoutInflater,
  private val covers: BookCoverProviderType,
  private val onToolbarBackPressed: () -> Unit,
  private val onShowErrorDetails: (TaskResult.Failure<*>) -> Unit,
  private val onBookSAMLDownloadRequested: (CatalogBookStatus<DownloadWaitingForExternalAuthentication>) -> Unit,
  private val onBookBorrowRequested: (CatalogBorrowParameters) -> Unit,
  private val onBookBorrowCancelRequested: (CatalogBookStatus<*>) -> Unit,
  private val onBookCanBeRevoked: (CatalogBookStatus<*>) -> Boolean,
  private val onBookPreviewOpenRequested: (CatalogBookStatus<*>) -> Unit,
  private val onBookRevokeRequested: (CatalogBookStatus<*>) -> Unit,
  private val onBookViewerOpen: (Book, BookFormat) -> Unit,
  private val onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit,
  private val onFeedSelected: (accountID: AccountID, title: String, uri: URI) -> Unit,
) : CatalogFeedView() {

  private var loanExpiryDate: DateTime? = null
  private val logger =
    LoggerFactory.getLogger(CatalogFeedViewDetails2::class.java)

  private val genreUriScheme =
    "http://librarysimplified.org/terms/genres/Simplified/"

  private val loanEndFormatter =
    DateTimeFormat.forPattern("MMM d, yyyy")

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

  private val appBar =
    this.root.findViewById<AppBarLayout>(R.id.bookD2AppBar)
  private val customToolbar =
    this.appBar.findViewById<View>(R.id.bookD2CustomToolbar)

  private val imageUnderlay =
    this.root.findViewById<ImageView>(R.id.bookD2ImageUnderlay)

  private val scrollView =
    this.root.findViewById<NestedScrollView>(R.id.bookD2ScrollView)
  private val scrollLinear =
    this.scrollView.findViewById<LinearLayout>(R.id.bookD2ScrollLinear)
  private val spacer =
    this.scrollLinear.findViewById<View>(R.id.bookD2Spacer)
  private val descriptionText =
    this.scrollView.findViewById<TextView>(R.id.bookD2Text)
  private val seeMore =
    this.scrollView.findViewById<TextView>(R.id.bookD2seeMoreText)
  private val metadata =
    this.scrollView.findViewById<TableLayout>(R.id.bookD2InformationTable)
  private val relatedLayout =
    this.scrollView.findViewById<ViewGroup>(R.id.bookD2RelatedBooksContainer)
  private val relatedListView =
    this.relatedLayout.findViewById<RecyclerView>(R.id.bookD2RelatedBooksList)
  private val relatedLoading =
    this.relatedLayout.findViewById<ProgressBar>(R.id.feedLoadProgress)
  private val relatedTitle =
    this.scrollView.findViewById<TextView>(R.id.bookD2RelatedBooksTitle)
  private val relatedDivider =
    this.scrollView.findViewById<View>(R.id.bookD2RelatedBooksDivider)
  private val relatedAdapter =
    CatalogFeedWithGroupsAdapter(
      covers = this.covers,
      screenSize = this.screenSize,
      laneStyle = CatalogFeedWithGroupsLaneViewHolder.LaneStyle.RELATED_BOOKS_LANE,
      onFeedSelected = this.onFeedSelected,
      onBookSelected = this.onBookSelected
    )

  private val toolbarItemsWhenCollapsed =
    this.root.findViewById<ViewGroup>(R.id.bookD2ToolbarItemsWhenCollapsed)
  private val toolbarTitle =
    this.toolbarItemsWhenCollapsed.findViewById<TextView>(R.id.bookD2ToolbarItemTitle)
  private val toolbarSubtitle =
    this.toolbarItemsWhenCollapsed.findViewById<TextView>(R.id.bookD2ToolbarItemSubtitle)

  private val toolbarItemsWhenExpanded =
    this.root.findViewById<ViewGroup>(R.id.bookD2ToolbarOverlay)
  private val backButton =
    this.toolbarItemsWhenExpanded.findViewById<View>(R.id.toolbarBackButton)
  private val backButtonImage =
    this.toolbarItemsWhenExpanded.findViewById<ImageView>(R.id.toolbarBackButtonImage)

  private val imageOverlay =
    this.root.findViewById<ViewGroup>(R.id.book2DImageOverlay)
  private val cover =
    this.imageOverlay.findViewById<ImageView>(R.id.book2DOverlayCover)
  private val bookTitle =
    this.imageOverlay.findViewById<TextView>(R.id.book2DOverlayTitle)
  private val bookSubtitle =
    this.imageOverlay.findViewById<TextView>(R.id.book2DOverlaySubtitle)
  private val bookButtons =
    this.imageOverlay.findViewById<ViewGroup>(R.id.book2DOverlayButtons)

  private val bottomSheetDarken =
    this.root.findViewById<View>(R.id.book2DBottomSheetDarken)
  private val bottomSheet =
    this.root.findViewById<ViewGroup>(R.id.book2DBottomSheet)
  private val bottomSheetCover =
    this.bottomSheet.findViewById<ImageView>(R.id.book2DBottomSheetCover)
  private val bottomSheetTitle =
    this.bottomSheet.findViewById<TextView>(R.id.book2DBottomSheetTitle)
  private val bottomSheetSubtitle =
    this.bottomSheet.findViewById<TextView>(R.id.book2DBottomSheetAuthors)
  private val bottomSheetLibrary =
    this.bottomSheet.findViewById<TextView>(R.id.book2DBottomSheetLibrary)
  private val bottomSheetInfoProgress =
    this.bottomSheet.findViewById<ProgressBar>(R.id.book2DBottomSheetProgress)
  private val bottomSheetBehavior =
    from(this.bottomSheet)
  private val bottomSheetInfoContainer =
    this.bottomSheet.findViewById<ViewGroup>(R.id.book2DBottomSheetInfoContainer)
  private val bottomSheetInfoBorrowingLayout =
    this.bottomSheetInfoContainer.findViewById<ViewGroup>(R.id.book2DBottomSheetInfoBorrowingLayout)
  private val bottomSheetInfoBorrowingText =
    this.bottomSheetInfoBorrowingLayout.findViewById<TextView>(R.id.book2DBottomSheetInfoBorrowingText)
  private val bottomSheetInfoBorrowingTime =
    this.bottomSheetInfoBorrowingLayout.findViewById<TextView>(R.id.book2DBottomSheetInfoBorrowingTime)
  private val bottomSheetInfoGenericLayout =
    this.bottomSheetInfoContainer.findViewById<ViewGroup>(R.id.book2DBottomSheetInfoGenericLayout)
  private val bottomSheetInfoGenericText =
    this.bottomSheetInfoGenericLayout.findViewById<TextView>(R.id.book2DBottomSheetInfoGenericText)

  private val bookButton0 =
    this.imageOverlay.findViewById<Button>(R.id.book2DOverlayButton0)
  private val bookButton1 =
    this.imageOverlay.findViewById<Button>(R.id.book2DOverlayButton1)
  private val bookToolbarButton =
    this.toolbarItemsWhenCollapsed.findViewById<Button>(R.id.bookD2ToolbarItemButton)
  private val bookBottomSheetButton0 =
    this.bottomSheet.findViewById<TextView>(R.id.book2DBottomSheetButton0)
  private val bookBottomSheetButton1 =
    this.bottomSheet.findViewById<TextView>(R.id.book2DBottomSheetButton1)

  /*
   * "Enabled" flags for views. There are two flags for each set of buttons, the "status"
   * flag and the "view" flag. The "status" flag is `true` or `false` depending on the current
   * book status, and the "view" flag is `true` or `false` depending on whether the app bar is
   * expanded or contracted. Both flags must be `true` for the buttons to be enabled.
   *
   * Essentially, we want the buttons to be disabled if they're not fully visible in the current
   * view OR the current book status suggests they should be disabled.
   */

  private var enableButtonsOnOverlayView = true
  private var enableButtonsOnToolbarView = false
  private var enableButton0Status = false
  private var enableButton1Status = false

  private fun isToolbarButtonsEnabled(): Boolean {
    return this.enableButton0Status && this.enableButtonsOnToolbarView
  }

  private fun isOverlayButton0Enabled(): Boolean {
    return this.enableButton0Status && this.enableButtonsOnOverlayView
  }

  private fun isOverlayButton1Enabled(): Boolean {
    return this.enableButton1Status && this.enableButtonsOnOverlayView
  }

  private fun reconfigureButtonsEnabledDisabled() {
    val toolbarEnabled =
      this.isToolbarButtonsEnabled()
    this.bookToolbarButton.isEnabled =
      toolbarEnabled
    this.bookButton0.isEnabled =
      this.isOverlayButton0Enabled()
    this.bookButton1.isEnabled =
      this.isOverlayButton1Enabled()

    /*
     * Because the bottom sheet is explicitly shown and hidden, the enabled/disabled state of
     * the buttons is purely based on the book status.
     */

    this.bookBottomSheetButton0.isEnabled =
      this.enableButton0Status
    this.bookBottomSheetButton1.isEnabled =
      this.enableButton1Status
  }

  private var spacerSize: Int
  private val spacerSizeMax: Int
  private val spacerSizeMin: Int

  private val bottomSheetDarkenOpacityMax = 1.0f

  private val debugText =
    this.root.findViewById<TextView>(R.id.bookD2TextDebug)

  init {
    this.setVisibility(this.debugText, View.INVISIBLE)

    /*
     * Control the opacity of a full-screen darkening view based on the bottom sheet. When the
     * bottom sheet is fully expanded, the darkening view is mostly opaque. When the bottom sheet
     * is fully collapsed, the darkening view is transparent.
     */

    this.setVisibility(this.bottomSheetInfoProgress, View.INVISIBLE)
    this.bottomSheetDarken.alpha = 0.0f
    this.bottomSheetBehavior.addBottomSheetCallback(object :
      BottomSheetCallback() {
      override fun onStateChanged(
        bottomSheet: View,
        newState: Int
      ) {
        val c = this@CatalogFeedViewDetails2
        when (newState) {
          STATE_EXPANDED -> {
            c.bottomSheetDarken.alpha = c.bottomSheetDarkenOpacityMax
          }

          STATE_COLLAPSED -> {
            c.bottomSheetDarken.alpha = 0.0f
          }

          STATE_HIDDEN -> {
            // Nothing required
          }

          STATE_DRAGGING -> {
            // Nothing required
          }

          STATE_HALF_EXPANDED -> {
            // Nothing required
          }

          STATE_SETTLING -> {
            // Nothing required
          }
        }
      }

      override fun onSlide(
        bottomSheet: View,
        slideOffset: Float
      ) {
        val c = this@CatalogFeedViewDetails2
        c.bottomSheetDarken.alpha =
          clamp(slideOffset, 0.0f, 1.0f) * c.bottomSheetDarkenOpacityMax
      }
    })

    this.scrollView.isSaveEnabled = false
    this.cover.bringToFront()

    /*
     * Configure the related view lane.
     */

    this.relatedListView.layoutManager = LinearLayoutManager(this.root.context)
    this.relatedListView.setHasFixedSize(true)
    (this.relatedListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    this.relatedListView.setItemViewCacheSize(8)
    this.relatedListView.addItemDecoration(
      CatalogFeedWithGroupsDecorator(this.screenSize.dpToPixels(16).toInt())
    )
    this.relatedListView.adapter = this.relatedAdapter

    this.spacerSize =
      this.spacer.height
    this.spacerSizeMax =
      this.root.resources.getDimensionPixelSize(R.dimen.catalogBookDetailScrollMarginTopMax)
    this.spacerSizeMin =
      this.root.resources.getDimensionPixelSize(R.dimen.catalogBookDetailScrollMarginTopMin)

    this.imageOverlay.y = this.adjustImageY(this.appBar, this.cover)
    this.appBar.addOnOffsetChangedListener { _, verticalOffset ->
      this.imageOverlay.y = this.adjustImageY(this.appBar, this.cover)

      val totalScrollRange =
        this.appBar.totalScrollRange
      val absOffset =
        Math.abs(verticalOffset)
      val offset =
        clamp(absOffset.toDouble() / totalScrollRange.toDouble(), 0.0, 1.0)

      val offsetExp =
        (offset * offset).toFloat()
      val offsetInv =
        (1.0 - offset)
      val offsetInvExpExp =
        (Math.pow(offsetInv, 8.0)).toFloat()

      this.toolbarItemsWhenCollapsed.alpha = offsetExp
      this.enableButtonsOnOverlayView = offsetInv >= 1.0
      this.enableButtonsOnToolbarView = offsetExp >= 1.0

      this.bookToolbarButton.isEnabled =
        this.isToolbarButtonsEnabled()
      this.bookButton0.isEnabled =
        this.isOverlayButton0Enabled()
      this.bookButton1.isEnabled =
        this.isOverlayButton1Enabled()

      this.backButton.isEnabled = offsetExp < 0.01
      this.backButton.alpha =
        if (this.backButton.isEnabled) {
          1.0f
        } else {
          0.0f
        }

      this.bookTitle.alpha = offsetInvExpExp
      this.bookSubtitle.alpha = offsetInvExpExp
      this.bookButtons.alpha = offsetInvExpExp

      this.cover.pivotX = this.cover.width / 2.0f
      this.cover.pivotY = 0.0f
      this.cover.scaleX = offsetInv.toFloat()
      this.cover.scaleY = offsetInv.toFloat()
      this.cover.alpha = offsetInv.toFloat()

      /*
       * Expand and contract the spacer in the scroll view. This is used
       * to move the contents of the scroll view up and down in order to follow the cover as
       * it expands and contracts.
       */

      val newSpacerSize = this.interpolate(this.spacerSizeMin, this.spacerSizeMax, offset)
      if (newSpacerSize != this.spacerSize) {
        this.spacer.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, newSpacerSize)
      }
    }

    /*
     * Request a layout in order to get all of the various objects above into their correct
     * starting positions on the page. If this isn't done, the objects won't be arranged correctly
     * until the user scrolls.
     */

    this.root.post(root::requestLayout)

    this.backButton.setOnClickListener {
      this.onToolbarBackPressed()
    }
  }

  private fun interpolate(
    minimum: Int,
    maximum: Int,
    position: Double
  ): Int {
    return ((maximum.toDouble() * (1.0 - position)) + (minimum.toDouble() * position)).toInt()
  }

  private fun adjustImageY(
    appBar: ViewGroup,
    cover: ImageView
  ): Float {
    this.logger.debug("Cover height: {}", cover.height)

    val coverHeight =
      this.root.resources.getDimensionPixelSize(R.dimen.catalogBookDetailCoverHeight)

    val initialY =
      appBar.height.toFloat()
    val withoutImage =
      initialY - coverHeight.toFloat()

    // Nudge the cover downwards slightly so that the bottom of the cover overhangs the
    // white area in the background.
    return withoutImage + 32.0f
  }

  fun bind(
    account: AccountProviderType,
    newEntry: FeedEntry.FeedEntryOPDS
  ) {
    val targetHeight =
      this.root.resources.getDimensionPixelSize(R.dimen.catalogBookDetailCoverHeight)

    val coverFutureMain =
      this.covers.loadCoverInto(
        entry = newEntry,
        imageView = this.cover,
        hasBadge = true,
        width = 0,
        height = targetHeight
      )

    coverFutureMain.addListener({
      UIThread.runOnUIThread { this.configureBackground() }
    }, MoreExecutors.directExecutor())

    val coverFutureBottom =
      this.covers.loadCoverInto(
        entry = newEntry,
        imageView = this.bottomSheetCover,
        hasBadge = true,
        width = 0,
        height = this.screenSize.dpToPixels(80).toInt()
      )

    this.bookTitle.text =
      newEntry.feedEntry.title
    this.bookSubtitle.text =
      newEntry.feedEntry.authorsCommaSeparated
    this.toolbarTitle.text =
      newEntry.feedEntry.title
    this.toolbarSubtitle.text =
      newEntry.feedEntry.authorsCommaSeparated
    this.bottomSheetTitle.text =
      newEntry.feedEntry.title
    this.bottomSheetSubtitle.text =
      newEntry.feedEntry.authorsCommaSeparated
    this.bottomSheetLibrary.text =
      account.displayName

    this.configureDescription(newEntry)
    this.configureMetadataTable(newEntry)
  }

  private fun bookInfoViewOf(): Triple<View, TextView, TextView> {
    val row = this.layoutInflater.inflate(R.layout.book_detail_metadata_item, this.metadata, false)
    val rowKey = row.findViewById<TextView>(R.id.key)
    val rowVal = row.findViewById<TextView>(R.id.value)
    return Triple(row, rowKey, rowVal)
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
      rowVal.text = this.formatDuration(durationValue)
      this.metadata.addView(row)
    }
  }

  fun setNoRelatedFeed() {
    this.logger.debug("Received no related feed.")
    this.setVisibility(this.relatedLayout, View.GONE)
    this.setVisibility(this.relatedTitle, View.GONE)
    this.setVisibility(this.relatedDivider, View.GONE)
  }

  fun bindRelatedFeedResult(
    feedResult: FeedLoaderResult
  ) {
    when (feedResult) {
      is FeedLoaderFailedAuthentication -> {
        this.setNoRelatedFeed()
      }

      is FeedLoaderFailedGeneral -> {
        this.setNoRelatedFeed()
      }

      is FeedLoaderSuccess -> {
        when (val feed = feedResult.feed) {
          is FeedWithGroups -> {
            this.logger.debug("Received a grouped related feed.")
            this.relatedAdapter.submitList(feed.feedGroupsInOrder)

            val laneCount = feed.feedGroupsInOrder.size
            val laneSize = this.screenSize.dpToPixels(128)
            val totalSize = (laneCount * laneSize).toInt()

            this.relatedListView.minimumHeight = totalSize
            this.setVisibility(this.relatedLayout, View.VISIBLE)
            this.setVisibility(this.relatedLoading, View.INVISIBLE)
            this.setVisibility(this.relatedListView, View.VISIBLE)
          }

          is FeedWithoutGroups -> {
            this.setNoRelatedFeed()
          }
        }
      }
    }
  }

  private fun configureDescription(
    newEntry: FeedEntry.FeedEntryOPDS
  ) {
    val opds = newEntry.feedEntry

    /*
     * Render the HTML present in the summary and insert it into the text view.
     */

    this.descriptionText.text =
      Html.fromHtml(opds.summary, Html.FROM_HTML_MODE_LEGACY)

    this.descriptionText.post {
      this.seeMore.visibility = if (this.descriptionText.lineCount > 6) {
        this.descriptionText.maxLines = 6
        this.seeMore.setOnClickListener {
          this.descriptionText.maxLines = Integer.MAX_VALUE
          this.setVisibility(this.seeMore, View.GONE)
        }
        View.VISIBLE
      } else {
        View.GONE
      }
    }
  }

  private fun configureBackground() {
    val rawDrawable =
      this.cover.drawable
    val rawBitmap =
      rawDrawable.toBitmap()

    val bgWidth = 8
    val bgHeight = 8

    val scaledBitmap =
      Bitmap.createScaledBitmap(rawBitmap, bgWidth, bgHeight, true)

    /*
     * Calculate the average color in the top row of the bitmap. We'll use the result to determine
     * whether text on top of the color should be black or white.
     */

    var r = 0
    var g = 0
    var b = 0

    for (x in 0 until bgWidth) {
      val c = scaledBitmap.get(x, 0)
      r += Color.red(c)
      g += Color.green(c)
      b += Color.blue(c)
    }

    r /= bgWidth
    g /= bgWidth
    b /= bgWidth

    val color =
      Color.argb(255, r, g, b)
    val luminance =
      ColorUtils.calculateLuminance(color)

    val textColor =
      if (luminance > 0.5) {
        Color.BLACK
      } else {
        Color.WHITE
      }

    this.backButtonImage.setColorFilter(textColor, PorterDuff.Mode.MULTIPLY)
    this.toolbarTitle.setTextColor(textColor)
    this.toolbarSubtitle.setTextColor(textColor)

    val textureDrawable = BitmapDrawable(this.root.resources, scaledBitmap)
    this.imageUnderlay.setImageDrawable(textureDrawable)
    this.imageUnderlay.scaleType = ImageView.ScaleType.FIT_XY
  }

  companion object {
    fun create(
      layoutInflater: LayoutInflater,
      screenSize: ScreenSizeInformationType,
      container: ViewGroup,
      covers: BookCoverProviderType,
      onShowErrorDetails: (TaskResult.Failure<*>) -> Unit,
      onBookSAMLDownloadRequested: (CatalogBookStatus<DownloadWaitingForExternalAuthentication>) -> Unit,
      onBookBorrowRequested: (CatalogBorrowParameters) -> Unit,
      onBookBorrowCancelRequested: (CatalogBookStatus<*>) -> Unit,
      onBookCanBeRevoked: (CatalogBookStatus<*>) -> Boolean,
      onBookPreviewOpenRequested: (CatalogBookStatus<*>) -> Unit,
      onBookRevokeRequested: (CatalogBookStatus<*>) -> Unit,
      onBookViewerOpen: (Book, BookFormat) -> Unit,
      onToolbarBackPressed: () -> Unit,
      onFeedSelected: (accountID: AccountID, title: String, uri: URI) -> Unit,
      onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit,
    ): CatalogFeedViewDetails2 {
      return CatalogFeedViewDetails2(
        root = layoutInflater.inflate(R.layout.book_detail2, container, true) as ViewGroup,
        screenSize = screenSize,
        layoutInflater = layoutInflater,
        covers = covers,
        onToolbarBackPressed = onToolbarBackPressed,
        onBookSAMLDownloadRequested = onBookSAMLDownloadRequested,
        onShowErrorDetails = onShowErrorDetails,
        onBookBorrowRequested = onBookBorrowRequested,
        onBookBorrowCancelRequested = onBookBorrowCancelRequested,
        onBookCanBeRevoked = onBookCanBeRevoked,
        onBookPreviewOpenRequested = onBookPreviewOpenRequested,
        onBookRevokeRequested = onBookRevokeRequested,
        onBookViewerOpen = onBookViewerOpen,
        onBookSelected = onBookSelected,
        onFeedSelected = onFeedSelected,
      )
    }
  }

  override fun clear() {
    this.root.isEnabled = false
  }

  fun onStatusUpdate(
    status: CatalogBookStatus<BookStatus>
  ) {
    this.debugText.text = status.status.javaClass.simpleName

    when (status.status) {
      is DownloadExternalAuthenticationInProgress -> {
        this.onBookStatusDownloadExternalAuthenticationInProgress(
          status as CatalogBookStatus<DownloadExternalAuthenticationInProgress>
        )
      }

      is DownloadWaitingForExternalAuthentication -> {
        this.onBookStatusDownloadWaitingForExternalAuthentication(
          status as CatalogBookStatus<DownloadWaitingForExternalAuthentication>
        )
      }

      is Downloading -> {
        this.onBookStatusDownloading(status as CatalogBookStatus<Downloading>)
      }

      is FailedDownload -> {
        this.onBookStatusFailedDownload(status as CatalogBookStatus<FailedDownload>)
      }

      is FailedLoan -> {
        this.onBookStatusFailedLoan(status as CatalogBookStatus<FailedLoan>)
      }

      is FailedRevoke -> {
        this.onBookStatusFailedRevoke(status as CatalogBookStatus<FailedRevoke>)
      }

      is HeldInQueue -> {
        this.onBookStatusHeldInQueue(status as CatalogBookStatus<HeldInQueue>)
      }

      is HeldReady -> {
        this.onBookStatusHeldReady(status as CatalogBookStatus<HeldReady>)
      }

      is Holdable -> {
        this.onBookStatusHoldable(status as CatalogBookStatus<Holdable>)
      }

      is Loanable -> {
        this.onBookStatusLoanable(status as CatalogBookStatus<Loanable>)
      }

      is LoanedDownloaded -> {
        this.onBookStatusLoanedDownloaded(status as CatalogBookStatus<LoanedDownloaded>)
      }

      is LoanedNotDownloaded -> {
        this.onBookStatusLoanedNotDownloaded(status as CatalogBookStatus<LoanedNotDownloaded>)
      }

      is ReachedLoanLimit -> {
        this.onBookStatusReachedLoanLimit(status as CatalogBookStatus<ReachedLoanLimit>)
      }

      is RequestingDownload -> {
        this.onBookStatusRequestingDownload(status as CatalogBookStatus<RequestingDownload>)
      }

      is RequestingLoan -> {
        this.onBookStatusRequestingLoan(status as CatalogBookStatus<RequestingLoan>)
      }

      is RequestingRevoke -> {
        this.onBookStatusRequestingRevoke(status as CatalogBookStatus<RequestingRevoke>)
      }

      is Revoked -> {
        this.onBookStatusRevoked(status as CatalogBookStatus<Revoked>)
      }
    }

    this.reconfigureButtonsEnabledDisabled()
  }

  private fun onBookStatusFailedLoan(
    status: CatalogBookStatus<FailedLoan>
  ) {
    this.enableButton0Status = true
    this.enableButton1Status = true

    this.configureInfoContainer(GENERIC) {
      this.bottomSheetInfoGenericText.setText(R.string.catalogOperationFailed)
    }

    this.reconfigureButton0(
      text = R.string.catalogRetry,
      actionInPage = {
        this.bottomSheetBehavior.state = STATE_EXPANDED
        this.onBookBorrowRequested(status.toBorrowParameters())
      },
      actionInBottomSheet = {
        this.onBookBorrowRequested(status.toBorrowParameters())
      }
    )
    this.reconfigureButton1(
      text = R.string.catalogDetails,
      actionInPage = { this.bottomSheetBehavior.state = STATE_EXPANDED },
      actionInBottomSheet = { this.onShowErrorDetails(status.status.result) }
    )
  }

  private fun onBookStatusFailedRevoke(
    status: CatalogBookStatus<FailedRevoke>
  ) {
    this.enableButton0Status = true
    this.enableButton1Status = true

    this.configureInfoContainer(GENERIC) {
      this.bottomSheetInfoGenericText.setText(R.string.catalogOperationFailed)
    }

    this.reconfigureButton0(
      text = R.string.catalogRetry,
      actionInPage = {
        this.bottomSheetBehavior.state = STATE_EXPANDED
        this.onBookRevokeRequested(status)
      },
      actionInBottomSheet = {
        this.onBookRevokeRequested(status)
      }
    )
    this.reconfigureButton1(
      text = R.string.catalogDetails,
      actionInPage = { this.bottomSheetBehavior.state = STATE_EXPANDED },
      actionInBottomSheet = { this.onShowErrorDetails(status.status.result) }
    )
  }

  private fun onBookStatusFailedDownload(
    status: CatalogBookStatus<FailedDownload>
  ) {
    this.enableButton0Status = true
    this.enableButton1Status = true

    this.configureInfoContainer(GENERIC) {
      this.bottomSheetInfoGenericText.setText(R.string.catalogOperationFailed)
    }

    this.reconfigureButton0(
      text = R.string.catalogRetry,
      actionInPage = {
        this.bottomSheetBehavior.state = STATE_EXPANDED
        this.onBookBorrowRequested(status.toBorrowParameters())
      },
      actionInBottomSheet = {
        this.onBookBorrowRequested(status.toBorrowParameters())
      }
    )
    this.reconfigureButton1(
      text = R.string.catalogDetails,
      actionInPage = { this.bottomSheetBehavior.state = STATE_EXPANDED },
      actionInBottomSheet = { this.onShowErrorDetails(status.status.result) }
    )
  }

  private fun onBookStatusHeldInQueue(
    status: CatalogBookStatus<HeldInQueue>
  ) {
    val held = status.status
    this.enableButton0Status = held.isRevocable

    /*
     * We currently ignore date information for holds. The hold end date would usually be
     * an estimate of when the hold will become available, but we don't reliably have this
     * information so currently we don't do anything with the date information that may
     * have been provided.
     */

    this.configureInfoContainer(GENERIC) {
      val queue = held.queuePosition
      if (queue != null) {
        this.bottomSheetInfoGenericText.text =
          this.bottomSheetInfoGenericText.resources.getString(
            R.string.catalogBookAvailabilityHeldQueue,
            queue
          )
      } else {
        this.bottomSheetInfoGenericText.text =
          this.bottomSheetInfoGenericText.resources.getString(
            R.string.catalogBookAvailabilityHeldIndefinite
          )
      }
    }

    this.reconfigureButton0(
      text = R.string.catalogCancelHold,
      actionInPage = {
        this.bottomSheetBehavior.state = STATE_EXPANDED
        this.onBookRevokeRequested(status)
      },
      actionInBottomSheet = {
        this.onBookRevokeRequested(status)
      }
    )

    if (held.isRevocable) {
      this.enableButton0Status = true
    }

    this.reconfigurePreviewButton(status)
  }

  enum class InfoState {
    NONE,
    BORROWING,
    GENERIC,
    PROGRESS,
    BORROWING_AND_PROGRESS
  }

  /**
   * Show or hide various parts of the info container based on the desired view state.
   */

  private fun configureInfoContainer(
    setState: InfoState,
    andThen: () -> Unit
  ) {
    return when (setState) {
      NONE -> {
        this.setVisibility(this.bottomSheetInfoContainer, View.INVISIBLE)
        this.setVisibility(this.bottomSheetInfoBorrowingLayout, View.INVISIBLE)
        this.setVisibility(this.bottomSheetInfoGenericLayout, View.INVISIBLE)
        this.setVisibility(this.bottomSheetInfoProgress, View.INVISIBLE)
        andThen()
      }
      BORROWING -> {
        this.setVisibility(this.bottomSheetInfoContainer, View.VISIBLE)
        this.setVisibility(this.bottomSheetInfoBorrowingLayout, View.VISIBLE)
        this.setVisibility(this.bottomSheetInfoGenericLayout, View.INVISIBLE)
        this.setVisibility(this.bottomSheetInfoProgress, View.INVISIBLE)
        andThen()
      }
      GENERIC -> {
        this.setVisibility(this.bottomSheetInfoContainer, View.VISIBLE)
        this.setVisibility(this.bottomSheetInfoBorrowingLayout, View.INVISIBLE)
        this.setVisibility(this.bottomSheetInfoGenericLayout, View.VISIBLE)
        this.setVisibility(this.bottomSheetInfoProgress, View.INVISIBLE)
        andThen()
      }
      PROGRESS -> {
        this.setVisibility(this.bottomSheetInfoContainer, View.VISIBLE)
        this.setVisibility(this.bottomSheetInfoBorrowingLayout, View.INVISIBLE)
        this.setVisibility(this.bottomSheetInfoGenericLayout, View.INVISIBLE)
        this.setVisibility(this.bottomSheetInfoProgress, View.VISIBLE)
        andThen()
      }
      BORROWING_AND_PROGRESS -> {
        this.setVisibility(this.bottomSheetInfoContainer, View.VISIBLE)
        this.setVisibility(this.bottomSheetInfoBorrowingLayout, View.VISIBLE)
        this.setVisibility(this.bottomSheetInfoGenericLayout, View.INVISIBLE)
        this.setVisibility(this.bottomSheetInfoProgress, View.VISIBLE)
        andThen()
      }
    }
  }

  private fun onBookStatusHeldReady(
    status: CatalogBookStatus<HeldReady>
  ) {
    this.enableButton0Status = true

    this.configureInfoContainer(GENERIC) {
      this.bottomSheetInfoGenericText.text =
        this.bottomSheetInfoBorrowingText.resources.getString(R.string.catalogBookAvailabilityLoanable)
    }

    this.reconfigureButton0(
      text = R.string.catalogGet,
      actionInPage = {
        this.bottomSheetBehavior.state = STATE_EXPANDED
        this.onBookBorrowRequested(status.toBorrowParameters())
      },
      actionInBottomSheet = {
        this.onBookBorrowRequested(status.toBorrowParameters())
      }
    )

    this.reconfigurePreviewButton(status)
  }

  private fun onBookStatusHoldable(
    status: CatalogBookStatus<Holdable>
  ) {
    this.enableButton0Status = true
    this.enableButton1Status = false

    this.configureInfoContainer(NONE) {}

    this.reconfigureButton0(
      text = R.string.catalogReserve,
      actionInPage = {
        this.bottomSheetBehavior.state = STATE_EXPANDED
        this.onBookBorrowRequested(status.toBorrowParameters())
      },
      actionInBottomSheet = {
        this.onBookBorrowRequested(status.toBorrowParameters())
      }
    )
    this.reconfigurePreviewButton(status)
  }

  private fun onBookStatusLoanable(
    status: CatalogBookStatus<Loanable>
  ) {
    this.enableButton0Status = true
    this.enableButton1Status = false

    this.configureInfoContainer(GENERIC) {
      this.bottomSheetInfoGenericText.setText(R.string.catalogBookAvailabilityLoanable)
    }

    this.reconfigureButton0(
      text = R.string.catalogGet,
      actionInPage = {
        this.bottomSheetBehavior.state = STATE_EXPANDED
        this.onBookBorrowRequested(status.toBorrowParameters())
      },
      actionInBottomSheet = {
        this.onBookBorrowRequested(status.toBorrowParameters())
      }
    )
    this.reconfigurePreviewButton(status)
  }

  private fun reconfigurePreviewButton(
    status: CatalogBookStatus<*>
  ) {
    when (status.previewStatus) {
      is BookPreviewStatus.HasPreview -> {
        this.enableButton1Status = true
        this.reconfigureButton1(
          text = R.string.catalogPreview,
          actionInPage = { this.onBookPreviewOpenRequested(status) },
          actionInBottomSheet = { this.onBookPreviewOpenRequested(status) }
        )
      }

      BookPreviewStatus.None -> {
        this.enableButton1Status = false
        this.bookButton1.visibility = View.GONE
        this.bookBottomSheetButton1.visibility = View.GONE
      }
    }
  }

  private fun onBookStatusLoanedDownloaded(
    status: CatalogBookStatus<LoanedDownloaded>
  ) {
    this.enableButton0Status = true
    this.enableButton1Status = false

    this.loanExpiryDate = status.status.loanExpiryDate
    if (this.loanExpiryDate != null) {
      this.configureInfoContainer(BORROWING) {
        this.bottomSheetInfoBorrowingText.setText(R.string.catalogBookDetailBorrowedUntil)
        this.bottomSheetInfoBorrowingTime.text = this.loanEndFormatter.print(this.loanExpiryDate)
      }
    } else {
      this.configureInfoContainer(GENERIC) {
        this.bottomSheetInfoGenericText.setText(R.string.catalogBookAvailabilityLoanedIndefinite)
      }
    }

    val format = status.book.findPreferredFormat()
    if (format != null) {
      this.reconfigureButton0(
        text = this.readButtonString(status.book),
        actionInPage = { this.onBookViewerOpen(status.book, format) },
        actionInBottomSheet = { this.onBookViewerOpen(status.book, format) }
      )
    }

    if (this.onBookCanBeRevoked.invoke(status)) {
      this.enableButton1Status = true
      this.reconfigureButton1(
        text = R.string.catalogReturn,
        actionInPage = {
          this.bottomSheetBehavior.state = STATE_EXPANDED
          this.onBookRevokeRequested(status)
        },
        actionInBottomSheet = {
          this.onBookRevokeRequested(status)
        }
      )
    } else {
      this.reconfigurePreviewButton(status)
    }
  }

  private fun readButtonString(
    book: Book
  ): Int {
    return when (book.findPreferredFormat()) {
      is BookFormat.BookFormatAudioBook -> R.string.catalogListen
      is BookFormat.BookFormatEPUB -> R.string.catalogRead
      is BookFormat.BookFormatPDF -> R.string.catalogRead
      null -> R.string.catalogRead
    }
  }

  private fun onBookStatusLoanedNotDownloaded(
    status: CatalogBookStatus<LoanedNotDownloaded>
  ) {
    this.enableButton0Status = true
    this.enableButton1Status = true

    this.loanExpiryDate = status.status.loanExpiryDate
    if (this.loanExpiryDate != null) {
      this.configureInfoContainer(BORROWING) {
        this.bottomSheetInfoBorrowingText.setText(R.string.catalogBookDetailBorrowedUntil)
        this.bottomSheetInfoBorrowingTime.text = this.loanEndFormatter.print(this.loanExpiryDate)
      }
    } else {
      this.configureInfoContainer(GENERIC) {
        this.bottomSheetInfoGenericText.setText(R.string.catalogBookAvailabilityLoanedIndefinite)
      }
    }

    this.reconfigureButton0(
      text = R.string.catalogGet,
      actionInPage = {
        this.bottomSheetBehavior.state = STATE_EXPANDED
        this.onBookBorrowRequested(status.toBorrowParameters())
      },
      actionInBottomSheet = {
        this.onBookBorrowRequested(status.toBorrowParameters())
      }
    )

    if (this.onBookCanBeRevoked.invoke(status)) {
      this.enableButton1Status = true
      this.reconfigureButton1(
        text = R.string.catalogReturn,
        actionInPage = {
          this.bottomSheetBehavior.state = STATE_EXPANDED
          this.onBookRevokeRequested(status)
        },
        actionInBottomSheet = {
          this.onBookRevokeRequested(status)
        }
      )
    } else {
      this.reconfigurePreviewButton(status)
    }
  }

  private fun onBookStatusReachedLoanLimit(
    status: CatalogBookStatus<ReachedLoanLimit>
  ) {
    this.enableButton0Status = false
    this.enableButton1Status = false

    this.configureInfoContainer(GENERIC) {
      this.bottomSheetInfoGenericText.setText(R.string.bookReachedLoanLimitDialogMessage)
    }
  }

  private fun onBookStatusRequestingDownload(
    status: CatalogBookStatus<RequestingDownload>
  ) {
    this.enableButton0Status = false
    this.enableButton1Status = false

    val expires = this.loanExpiryDate
    if (expires != null) {
      this.configureInfoContainer(BORROWING_AND_PROGRESS) {
        this.setBorrowingTimeDays(expires)
        this.setProgress(0.0)
      }
    } else {
      this.configureInfoContainer(PROGRESS) {
        this.setProgress(0.0)
      }
    }
  }

  private fun setBorrowingTimeDays(
    expires: DateTime?
  ) {
    if (expires == null) {
      return
    }
    val today = DateTime.now()
    val days = Days.daysBetween(today, expires)
    this.bottomSheetInfoBorrowingText.setText(R.string.catalogBookDetailBorrowingFor)
    this.bottomSheetInfoBorrowingTime.text =
      this.bottomSheetInfoBorrowingTime.resources.getString(
        R.string.catalogBookDaysRemaining,
        days.days.toString()
      )
  }

  private fun onBookStatusRequestingLoan(
    status: CatalogBookStatus<RequestingLoan>
  ) {
    this.enableButton0Status = false
    this.enableButton1Status = false

    this.configureInfoContainer(PROGRESS) {
      this.setProgress(0.0)
    }
  }

  private fun onBookStatusRequestingRevoke(
    status: CatalogBookStatus<RequestingRevoke>
  ) {
    this.enableButton0Status = false
    this.enableButton1Status = false

    this.configureInfoContainer(PROGRESS) {
      this.setProgress(0.0)
    }
  }

  private fun onBookStatusRevoked(
    status: CatalogBookStatus<Revoked>
  ) {
    this.enableButton0Status = false
    this.enableButton1Status = false

    this.configureInfoContainer(NONE) {}
  }

  private fun onBookStatusDownloading(
    status: CatalogBookStatus<Downloading>
  ) {
    this.enableButton0Status = true
    this.enableButton1Status = false

    val expires = this.loanExpiryDate
    if (expires != null) {
      this.configureInfoContainer(BORROWING_AND_PROGRESS) {
        this.setBorrowingTimeDays(expires)
        this.setProgress(status.status.progressPercent)
      }
    } else {
      this.configureInfoContainer(PROGRESS) {
        this.setProgress(status.status.progressPercent)
      }
    }

    this.reconfigureButton0(
      text = R.string.catalogCancel,
      actionInPage = {
        this.bottomSheetBehavior.state = STATE_EXPANDED
        this.onBookBorrowCancelRequested(status)
      },
      actionInBottomSheet = {
        this.onBookBorrowCancelRequested(status)
      }
    )
  }

  private fun setProgress(
    percent: Double?
  ) {
    this.bottomSheetInfoProgress.setProgress((percent ?: 0.0).toInt(), true)
  }

  private fun onBookStatusDownloadWaitingForExternalAuthentication(
    status: CatalogBookStatus<DownloadWaitingForExternalAuthentication>
  ) {
    val expires = this.loanExpiryDate
    if (expires != null) {
      this.configureInfoContainer(BORROWING_AND_PROGRESS) {
        this.setBorrowingTimeDays(expires)
        this.setProgress(0.0)
      }
    } else {
      this.configureInfoContainer(PROGRESS) {
        this.setProgress(0.0)
      }
    }

    this.onBookSAMLDownloadRequested(status)
  }

  private fun onBookStatusDownloadExternalAuthenticationInProgress(
    status: CatalogBookStatus<DownloadExternalAuthenticationInProgress>
  ) {
    val expires = this.loanExpiryDate
    if (expires != null) {
      this.configureInfoContainer(BORROWING_AND_PROGRESS) {
        this.setBorrowingTimeDays(expires)
        this.setProgress(0.0)
      }
    } else {
      this.configureInfoContainer(PROGRESS) {
        this.setProgress(0.0)
      }
    }
  }

  /**
   * Reconfigure "button 0" in all the various views. The button is configured to execute
   * the [actionInPage] in page action if it is clicked in the toolbar or overlay, and the
   * [actionInBottomSheet] if it is clicked in the bottom sheet.
   */

  private fun reconfigureButton0(
    @StringRes text: Int,
    actionInPage: () -> Unit,
    actionInBottomSheet: () -> Unit
  ) {
    this.bookButton0.setText(text)
    this.bookButton0.setOnClickListener { actionInPage() }
    this.bookBottomSheetButton0.setText(text)
    this.bookBottomSheetButton0.setOnClickListener { actionInBottomSheet() }
    this.bookToolbarButton.setText(text)
    this.bookToolbarButton.setOnClickListener { actionInPage() }
  }

  /**
   * Reconfigure "button 1" in all the various views. The button is configured to execute
   * the [actionInPage] in page action if it is clicked in the overlay, and the
   * [actionInBottomSheet] if it is clicked in the bottom sheet.
   */

  private fun reconfigureButton1(
    @StringRes text: Int,
    actionInPage: () -> Unit,
    actionInBottomSheet: () -> Unit
  ) {
    this.bookButton1.visibility = View.VISIBLE
    this.bookButton1.setText(text)
    this.bookButton1.setOnClickListener { actionInPage() }
    this.bookBottomSheetButton1.visibility = View.VISIBLE
    this.bookBottomSheetButton1.setText(text)
    this.bookBottomSheetButton1.setOnClickListener { actionInBottomSheet() }
  }

  /**
   * Absurdly, there is a cost to setting the visibility of a view in Android, even if the
   * visibility value being set is already the visibility value of the given view.
   */

  private fun setVisibility(
    view: View,
    visibility: Int
  ) {
    if (view.visibility != visibility) {
      view.visibility = visibility
    }
  }
}
