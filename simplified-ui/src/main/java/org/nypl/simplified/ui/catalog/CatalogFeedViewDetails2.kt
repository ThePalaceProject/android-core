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
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.core.math.MathUtils.clamp
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.common.util.concurrent.MoreExecutors
import com.io7m.jfunctional.Some
import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.format.DateTimeFormatterBuilder
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.Feed.FeedWithGroups
import org.nypl.simplified.feeds.api.Feed.FeedWithoutGroups
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedLoaderResult
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedAuthentication
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderFailure.FeedLoaderFailedGeneral
import org.nypl.simplified.feeds.api.FeedLoaderResult.FeedLoaderSuccess
import org.nypl.simplified.threads.UIThread
import org.slf4j.LoggerFactory
import java.net.URI

class CatalogFeedViewDetails2(
  override val root: ViewGroup,
  private val layoutInflater: LayoutInflater,
  private val covers: BookCoverProviderType,
  private val onToolbarBackPressed: () -> Unit,
  private val onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit,
  private val onFeedSelected: (accountID: AccountID, title: String, uri: URI) -> Unit,
) : CatalogFeedView() {

  private val logger =
    LoggerFactory.getLogger(CatalogFeedViewDetails2::class.java)

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
  private val relatedAdapter =
    CatalogFeedWithGroupsAdapter(
      covers = this.covers,
      onFeedSelected = this.onFeedSelected,
      onBookSelected = this.onBookSelected
    )

  private val toolbarItemsWhenCollapsed =
    this.root.findViewById<ViewGroup>(R.id.bookD2ToolbarItemsWhenCollapsed)
  private val toolbarBorrowButton =
    this.toolbarItemsWhenCollapsed.findViewById<Button>(R.id.bookD2ToolbarItemBorrow)
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

  private var spacerSize: Int
  private val spacerSizeMax: Int
  private val spacerSizeMin: Int

  init {
    this.scrollView.isSaveEnabled = false
    this.cover.bringToFront()
    this.backButton.setOnClickListener { this.onToolbarBackPressed() }

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

      this.toolbarBorrowButton.isEnabled = offsetExp >= 1.0
      this.toolbarItemsWhenCollapsed.alpha = offsetExp

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
    newEntry: FeedEntry.FeedEntryOPDS
  ) {
    val targetHeight =
      this.root.resources.getDimensionPixelSize(R.dimen.catalogBookDetailCoverHeight)

    val coverFuture =
      this.covers.loadCoverInto(
        entry = newEntry,
        imageView = this.cover,
        hasBadge = true,
        width = 0,
        height = targetHeight
      )

    coverFuture.addListener({
      UIThread.runOnUIThread { this.configureBackground() }
    }, MoreExecutors.directExecutor())

    this.bookTitle.text =
      newEntry.feedEntry.title
    this.bookSubtitle.text =
      newEntry.feedEntry.authorsCommaSeparated
    this.toolbarTitle.text =
      newEntry.feedEntry.title
    this.toolbarSubtitle.text =
      newEntry.feedEntry.authorsCommaSeparated

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
          this.seeMore.visibility = View.GONE
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
      container: ViewGroup,
      covers: BookCoverProviderType,
      onToolbarBackPressed: () -> Unit,
      onFeedSelected: (accountID: AccountID, title: String, uri: URI) -> Unit,
      onBookSelected: (FeedEntry.FeedEntryOPDS) -> Unit,
    ): CatalogFeedViewDetails2 {
      return CatalogFeedViewDetails2(
        root = layoutInflater.inflate(R.layout.book_detail2, container, true) as ViewGroup,
        layoutInflater = layoutInflater,
        covers = covers,
        onToolbarBackPressed = onToolbarBackPressed,
        onFeedSelected = onFeedSelected,
        onBookSelected = onBookSelected,
      )
    }
  }

  override fun clear() {
    this.root.isEnabled = false
  }

  fun onStatusUpdate(
    catalogBookStatus: CatalogBookStatus<BookStatus>
  ) {
    // XXX: TODO
  }
}
