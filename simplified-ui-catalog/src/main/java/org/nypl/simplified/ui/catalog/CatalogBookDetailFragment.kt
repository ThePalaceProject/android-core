package org.nypl.simplified.ui.catalog

import android.os.Bundle
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.common.base.Preconditions
import com.google.common.util.concurrent.FluentFuture
import com.io7m.jfunctional.Some
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatterBuilder
import org.librarysimplified.services.api.Services
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.book_registry.BookPreviewStatus
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.FeedEntry.FeedEntryOPDS
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.neutrality.NeutralToolbar
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * A book detail page.
 */

class CatalogBookDetailFragment : Fragment(R.layout.book_detail) {

  companion object {

    private const val PARAMETERS_ID =
      "org.nypl.simplified.ui.catalog.CatalogFragmentBookDetail.parameters"

    /**
     * Create a book detail fragment for the given parameters.
     */

    fun create(parameters: CatalogBookDetailFragmentParameters): CatalogBookDetailFragment {
      val fragment = CatalogBookDetailFragment()
      fragment.arguments = bundleOf(this.PARAMETERS_ID to parameters)
      return fragment
    }
  }

  private val logger = LoggerFactory.getLogger(CatalogBookDetailFragment::class.java)

  private val services =
    Services.serviceDirectory()

  private val bookCovers =
    services.requireService(BookCoverProviderType::class.java)

  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)

  private val screenInformation =
    services.requireService(ScreenSizeInformationType::class.java)

  private val parameters: CatalogBookDetailFragmentParameters by lazy {
    this.requireArguments()[PARAMETERS_ID] as CatalogBookDetailFragmentParameters
  }

  private val listener: FragmentListenerType<CatalogBookDetailEvent> by fragmentListeners()

  private val borrowViewModel: CatalogBorrowViewModel by viewModels(
    factoryProducer = {
      CatalogBorrowViewModelFactory(services)
    }
  )

  private val viewModel: CatalogBookDetailViewModel by viewModels(
    factoryProducer = {
      CatalogBookDetailViewModelFactory(
        this.services,
        this.borrowViewModel,
        this.listener,
        this.parameters
      )
    }
  )

  private var thumbnailLoading: FluentFuture<Unit>? = null

  private lateinit var authors: TextView
  private lateinit var buildConfig: BuildConfigurationServiceType
  private lateinit var buttonCreator: CatalogButtons
  private lateinit var buttons: LinearLayout
  private lateinit var cover: ImageView
  private lateinit var covers: BookCoverProviderType
  private lateinit var debugStatus: TextView
  private lateinit var feedWithGroupsAdapter: CatalogFeedWithGroupsAdapter
  private lateinit var feedWithoutGroupsAdapter: CatalogPagedAdapter
  private lateinit var metadata: LinearLayout
  private lateinit var relatedBooksContainer: FrameLayout
  private lateinit var relatedBooksList: RecyclerView
  private lateinit var relatedBooksLoading: ViewGroup
  private lateinit var report: TextView
  private lateinit var screenSize: ScreenSizeInformationType
  private lateinit var seeMore: TextView
  private lateinit var status: ViewGroup
  private lateinit var statusFailed: ViewGroup
  private lateinit var statusFailedText: TextView
  private lateinit var statusIdle: ViewGroup
  private lateinit var statusIdleText: TextView
  private lateinit var statusInProgress: ViewGroup
  private lateinit var statusInProgressBar: ProgressBar
  private lateinit var statusInProgressText: TextView
  private lateinit var summary: TextView
  private lateinit var title: TextView
  private lateinit var toolbar: NeutralToolbar

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

  private val feedWithGroupsData: MutableList<FeedGroup> = mutableListOf()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    this.screenSize =
      services.requireService(ScreenSizeInformationType::class.java)
    this.covers =
      services.requireService(BookCoverProviderType::class.java)
    this.buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    this.buttonCreator =
      CatalogButtons(this.requireContext(), this.screenSize)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbar =
      view.rootView.findViewWithTag(NeutralToolbar.neutralToolbarName)

    this.viewModel.bookWithStatusLive.observe(this.viewLifecycleOwner) { info ->
      reconfigureUI(info.first, info.second)
    }
    this.cover =
      view.findViewById(R.id.bookDetailCoverImage)
    this.title =
      view.findViewById(R.id.bookDetailTitle)
    this.authors =
      view.findViewById(R.id.bookDetailAuthors)
    this.seeMore =
      view.findViewById(R.id.seeMoreText)
    this.status =
      view.findViewById(R.id.bookDetailStatus)
    this.summary =
      view.findViewById(R.id.bookDetailDescriptionText)
    this.metadata =
      view.findViewById(R.id.bookDetailMetadataTable)
    this.buttons =
      view.findViewById(R.id.bookDetailButtons)
    this.relatedBooksContainer =
      view.findViewById(R.id.bookDetailRelatedBooksContainer)
    this.relatedBooksList =
      view.findViewById(R.id.relatedBooksList)
    this.relatedBooksLoading =
      view.findViewById(R.id.feedLoading)
    this.report =
      view.findViewById(R.id.bookDetailReport)

    this.debugStatus =
      view.findViewById(R.id.bookDetailDebugStatus)

    this.statusIdle =
      this.status.findViewById(R.id.bookDetailStatusIdle)
    this.statusIdleText =
      this.statusIdle.findViewById(R.id.idleText)

    this.statusInProgress =
      this.status.findViewById(R.id.bookDetailStatusInProgress)
    this.statusInProgressBar =
      this.statusInProgress.findViewById(R.id.inProgressBar)
    this.statusInProgressText =
      this.statusInProgress.findViewById(R.id.inProgressText)

    this.statusInProgressText.text = "100%"

    this.statusFailed =
      this.status.findViewById(R.id.bookDetailStatusFailed)
    this.statusFailedText =
      this.status.findViewById(R.id.failedText)

    this.statusIdle.visibility = View.VISIBLE
    this.statusInProgress.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.debugStatus.visibility =
      if (this.viewModel.showDebugBookDetailStatus) {
        View.VISIBLE
      } else {
        View.INVISIBLE
      }

    val targetHeight =
      this.resources.getDimensionPixelSize(R.dimen.cover_detail_height)
    this.covers.loadCoverInto(
      this.parameters.feedEntry, this.cover, hasBadge = true, 0, targetHeight
    )

    this.relatedBooksList.setHasFixedSize(true)
    this.relatedBooksList.setItemViewCacheSize(32)
    this.relatedBooksList.layoutManager = LinearLayoutManager(this.context)
    (this.relatedBooksList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    this.relatedBooksList.addItemDecoration(
      CatalogFeedWithGroupsDecorator(this.screenInformation.dpToPixels(16).toInt())
    )

    this.feedWithGroupsAdapter =
      CatalogFeedWithGroupsAdapter(
        groups = this.feedWithGroupsData,
        coverLoader = this.bookCovers,
        onFeedSelected = this.viewModel::openFeed,
        onBookSelected = this.viewModel::openBookDetail
      )

    this.configureOPDSEntry(this.parameters.feedEntry)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    this.thumbnailLoading?.cancel(true)
    this.thumbnailLoading = null
  }

  override fun onResume() {
    super.onResume()
    this.configureToolbar()
  }

  override fun onStart() {
    super.onStart()
    this.configureToolbar()
  }

  private fun configureToolbar() {
    val actionBar = this.supportActionBar ?: return
    actionBar.setDisplayHomeAsUpEnabled(true)
    actionBar.setHomeActionContentDescription(null)
    actionBar.show()
    this.toolbar.title = ""
    this.toolbar.setLogoOnClickListener {
      this.viewModel.goUpwards()
    }
    return
  }

//  private fun configurePreviewButton(previewStatus: BookPreviewStatus) {
//    val feedEntry = this.parameters.feedEntry
//    this.previewButton.apply {
//      isVisible = previewStatus != BookPreviewStatus.None
//      setText(
//        if (feedEntry.probableFormat == BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO) {
//          R.string.catalogBookPreviewAudioBook
//        } else {
//          R.string.catalogBookPreviewBook
//        }
//      )
//      setOnClickListener {
//        viewModel.openBookPreview(this@CatalogBookDetailFragment.parameters.feedEntry)
//      }
//    }
//  }

  private fun configureOPDSEntry(feedEntry: FeedEntryOPDS) {
    val opds = feedEntry.feedEntry
    this.title.text = opds.title
    this.authors.text = opds.authorsCommaSeparated

    this.cover.contentDescription =
      CatalogBookAccessibilityStrings.coverDescription(this.resources, feedEntry)

    /*
     * Render the HTML present in the summary and insert it into the text view.
     */

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
      this.summary.text = Html.fromHtml(opds.summary, Html.FROM_HTML_MODE_LEGACY)
    } else {
      @Suppress("DEPRECATION")
      this.summary.text = Html.fromHtml(opds.summary)
    }

    this.summary.post {
      this.seeMore.visibility = if (this.summary.lineCount > 6) {
        this.summary.maxLines = 6
        this.seeMore.setOnClickListener {
          this.summary.maxLines = Integer.MAX_VALUE
          this.seeMore.visibility = View.GONE
        }
        View.VISIBLE
      } else {
        View.GONE
      }
    }

    this.configureMetadataTable(feedEntry.probableFormat, opds)

    /*
     * If there's a related feed, enable the "Related books..." item and open the feed
     * on demand.
     */

    val feedRelatedOpt = feedEntry.feedEntry.related
    if (feedRelatedOpt is Some<URI>) {
      val feedRelated = feedRelatedOpt.get()

      this.viewModel.relatedBooksFeedState.observe(
        this.viewLifecycleOwner,
        this::updateRelatedBooksUI
      )
      this.relatedBooksContainer.visibility = View.VISIBLE
      this.viewModel.loadRelatedBooks(feedRelated)
    } else {
      this.relatedBooksContainer.visibility = View.GONE
    }
  }

  private val genreUriScheme =
    "http://librarysimplified.org/terms/genres/Simplified/"

  private fun configureMetadataTable(
    probableFormat: BookFormats.BookFormatDefinition?,
    entry: OPDSAcquisitionFeedEntry
  ) {
    this.metadata.removeAllViews()

    val bookFormatText = when (probableFormat) {
      BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB ->
        getString(R.string.catalogBookFormatEPUB)
      BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO ->
        getString(R.string.catalogBookFormatAudioBook)
      BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF ->
        getString(R.string.catalogBookFormatPDF)
      else -> {
        ""
      }
    }

    if (bookFormatText.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaFormat)
      rowVal.text = bookFormatText
      this.metadata.addView(row)
    }

    val publishedOpt = entry.published
    if (publishedOpt is Some<DateTime>) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaPublicationDate)
      rowVal.text = this.dateFormatter.print(publishedOpt.get())
      this.metadata.addView(row)
    }

    val publisherOpt = entry.publisher
    if (publisherOpt is Some<String>) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaPublisher)
      rowVal.text = publisherOpt.get()
      this.metadata.addView(row)
    }

    if (entry.distribution.isNotBlank()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaDistributor)
      rowVal.text = entry.distribution
      this.metadata.addView(row)
    }

    val categories =
      entry.categories.filter { opdsCategory -> opdsCategory.scheme == this.genreUriScheme }
    if (categories.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaCategories)
      rowVal.text = categories.joinToString(", ") { opdsCategory -> opdsCategory.effectiveLabel }
      this.metadata.addView(row)
    }

    val narrators = entry.narrators.filterNot { it.isBlank() }
    if (narrators.isNotEmpty()) {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaNarrators)
      rowVal.text = narrators.joinToString(", ")
      this.metadata.addView(row)
    }

    this.run {
      val (row, rowKey, rowVal) = this.bookInfoViewOf()
      rowKey.text = this.getString(R.string.catalogMetaUpdatedDate)
      rowVal.text = this.dateTimeFormatter.print(entry.updated)
      this.metadata.addView(row)
    }
  }

  private fun bookInfoViewOf(): Triple<View, TextView, TextView> {
    val row = this.layoutInflater.inflate(R.layout.book_detail_metadata_item, this.metadata, false)
    val rowKey = row.findViewById<TextView>(R.id.key)
    val rowVal = row.findViewById<TextView>(R.id.value)
    return Triple(row, rowKey, rowVal)
  }

  private fun reconfigureUI(book: BookWithStatus, bookPreviewStatus: BookPreviewStatus) {
    this.debugStatus.text = book.javaClass.simpleName

    when (val status = book.status) {
      is BookStatus.Held -> {
        this.onBookStatusHeld(status, bookPreviewStatus)
      }
      is BookStatus.Loaned -> {
        this.onBookStatusLoaned(status, book.book, bookPreviewStatus)
      }
      is BookStatus.Holdable -> {
        this.onBookStatusHoldable(status, bookPreviewStatus)
      }
      is BookStatus.Loanable -> {
        this.onBookStatusLoanable(status, bookPreviewStatus)
      }
      is BookStatus.RequestingLoan -> {
        this.onBookStatusRequestingLoan()
      }
      is BookStatus.Revoked -> {
        this.onBookStatusRevoked(status)
      }
      is BookStatus.FailedLoan -> {
        this.onBookStatusFailedLoan(status)
      }
      is BookStatus.ReachedLoanLimit -> {
        this.onBookStatusReachedLoanLimit()
      }
      is BookStatus.FailedRevoke -> {
        this.onBookStatusFailedRevoke(status)
      }
      is BookStatus.FailedDownload -> {
        this.onBookStatusFailedDownload(status)
      }
      is BookStatus.RequestingRevoke -> {
        this.onBookStatusRequestingRevoke()
      }
      is BookStatus.RequestingDownload -> {
        this.onBookStatusRequestingDownload()
      }
      is BookStatus.Downloading -> {
        this.onBookStatusDownloading(status)
      }
      is BookStatus.DownloadWaitingForExternalAuthentication -> {
        this.onBookStatusDownloadWaitingForExternalAuthentication()
      }
      is BookStatus.DownloadExternalAuthenticationInProgress -> {
        this.onBookStatusDownloadExternalAuthenticationInProgress()
      }
    }
  }

  private fun updateRelatedBooksUI(feedState: CatalogFeedState?) {
    when (feedState) {
      is CatalogFeedState.CatalogFeedLoading -> {
        this.onCatalogFeedLoading()
      }
      is CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithGroups -> {
        this.onCatalogFeedWithGroups(feedState)
      }
      is CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithoutGroups -> {
        this.onCatalogFeedWithoutGroups(feedState)
      }
      else -> {
        this.relatedBooksContainer.visibility = View.GONE
      }
    }
  }

  private fun onCatalogFeedLoading() {
    this.relatedBooksContainer.visibility = View.VISIBLE
    this.relatedBooksList.visibility = View.INVISIBLE
    this.relatedBooksLoading.visibility = View.VISIBLE
  }

  private fun onCatalogFeedWithoutGroups(
    feedState: CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithoutGroups
  ) {
    this.relatedBooksContainer.visibility = View.VISIBLE
    this.relatedBooksLoading.visibility = View.INVISIBLE
    this.relatedBooksList.visibility = View.VISIBLE

    this.feedWithoutGroupsAdapter =
      CatalogPagedAdapter(
        context = requireActivity(),
        listener = this.viewModel,
        buttonCreator = this.buttonCreator,
        bookCovers = this.bookCovers,
        profilesController = this.profilesController
      )

    this.relatedBooksList.adapter = this.feedWithoutGroupsAdapter
    feedState.entries.observe(this) { newPagedList ->
      this.logger.debug("received paged list ({} elements)", newPagedList.size)
      this.feedWithoutGroupsAdapter.submitList(newPagedList)
    }
  }

  private fun onCatalogFeedWithGroups(
    feedState: CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithGroups
  ) {
    this.relatedBooksContainer.visibility = View.VISIBLE
    this.relatedBooksLoading.visibility = View.INVISIBLE
    this.relatedBooksList.visibility = View.VISIBLE

    this.relatedBooksList.adapter = this.feedWithGroupsAdapter
    this.feedWithGroupsData.clear()
    this.feedWithGroupsData.addAll(feedState.feed.feedGroupsInOrder)
    this.feedWithGroupsAdapter.notifyDataSetChanged()
  }

  private fun onBookStatusFailedLoan(
    bookStatus: BookStatus.FailedLoan,
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(
      this.buttonCreator.createDismissButton {
        this.viewModel.dismissBorrowError()
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createDetailsButton {
        this.viewModel.showError(bookStatus.result)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createRetryButton {
        this.viewModel.borrowMaybeAuthenticated()
      }
    )
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.VISIBLE
    this.statusFailedText.text = this.resources.getText(R.string.catalogOperationFailed)
  }

  private fun onBookStatusRevoked(
    bookStatus: BookStatus.Revoked
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.resources, bookStatus)
  }

  private fun onBookStatusRequestingLoan() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onBookStatusLoanable(
    bookStatus: BookStatus.Loanable,
    bookPreviewStatus: BookPreviewStatus
  ) {
    this.buttons.removeAllViews()

    val createPreviewButton = bookPreviewStatus != BookPreviewStatus.None

    if (createPreviewButton) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }

    this.buttons.addView(
      this.buttonCreator.createGetButton(
        onClick = {
          this.viewModel.borrowMaybeAuthenticated()
        }
      )
    )

    if (createPreviewButton) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createReadPreviewButton(
          bookFormat = parameters.feedEntry.probableFormat,
          onClick = {
            viewModel.openBookPreview(parameters.feedEntry)
          }
        )
      )
      this.buttons.addView(this.buttonCreator.createButtonSpace())
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.resources, bookStatus)
  }

  private fun onBookStatusReachedLoanLimit() {
    AlertDialog.Builder(requireContext())
      .setTitle(R.string.bookReachedLoanLimitDialogTitle)
      .setMessage(R.string.bookReachedLoanLimitDialogMessage)
      .setPositiveButton(R.string.bookReachedLoanLimitDialogButton) { dialog, _ ->
        dialog.dismiss()
      }
      .create()
      .show()

    viewModel.resetInitialBookStatus(this.parameters.feedEntry)
  }

  private fun onBookStatusHoldable(
    bookStatus: BookStatus.Holdable,
    bookPreviewStatus: BookPreviewStatus
  ) {
    this.buttons.removeAllViews()

    val createPreviewButton = bookPreviewStatus != BookPreviewStatus.None

    if (createPreviewButton) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }

    this.buttons.addView(
      this.buttonCreator.createReserveButton(
        onClick = {
          this.viewModel.reserveMaybeAuthenticated()
        }
      )
    )

    if (createPreviewButton) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createReadPreviewButton(
          bookFormat = parameters.feedEntry.probableFormat,
          onClick = {
            viewModel.openBookPreview(parameters.feedEntry)
          }
        )
      )
      this.buttons.addView(this.buttonCreator.createButtonSpace())
    } else {
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }

    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.resources, bookStatus)
  }

  private fun onBookStatusHeld(
    bookStatus: BookStatus.Held,
    bookPreviewStatus: BookPreviewStatus
  ) {
    this.buttons.removeAllViews()

    val createPreviewButton = bookPreviewStatus != BookPreviewStatus.None

    when (bookStatus) {
      is BookStatus.Held.HeldInQueue -> {
        if (createPreviewButton) {
          this.buttons.addView(
            this.buttonCreator.createReadPreviewButton(
              bookFormat = parameters.feedEntry.probableFormat,
              onClick = {
                viewModel.openBookPreview(parameters.feedEntry)
              }
            )
          )
          this.buttons.addView(this.buttonCreator.createButtonSpace())
        } else {
          this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
        }

        if (bookStatus.isRevocable) {
          this.buttons.addView(
            this.buttonCreator.createRevokeHoldButton(
              onClick = {
                this.viewModel.revokeMaybeAuthenticated()
              }
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

      is BookStatus.Held.HeldReady -> {
        // if there will be at least one more button
        if (createPreviewButton || bookStatus.isRevocable) {
          this.buttons.addView(this.buttonCreator.createButtonSpace())
        } else {
          this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
        }

        this.buttons.addView(
          this.buttonCreator.createGetButton(
            onClick = {
              this.viewModel.borrowMaybeAuthenticated()
            }
          )
        )

        if (createPreviewButton) {
          this.buttons.addView(this.buttonCreator.createButtonSpace())

          this.buttons.addView(
            this.buttonCreator.createReadPreviewButton(
              bookFormat = parameters.feedEntry.probableFormat,
              onClick = {
                viewModel.openBookPreview(parameters.feedEntry)
              }
            )
          )
        }

        if (bookStatus.isRevocable) {
          this.buttons.addView(this.buttonCreator.createButtonSpace())

          this.buttons.addView(
            this.buttonCreator.createRevokeHoldButton(
              onClick = {
                this.viewModel.revokeMaybeAuthenticated()
              }
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
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.resources, bookStatus)
  }

  private fun onBookStatusLoaned(
    bookStatus: BookStatus.Loaned,
    book: Book,
    bookPreviewStatus: BookPreviewStatus
  ) {
    this.buttons.removeAllViews()

    var createPreviewButton = bookPreviewStatus != BookPreviewStatus.None

    when (bookStatus) {
      is BookStatus.Loaned.LoanedNotDownloaded -> {
        this.buttons.addView(
          if (bookStatus.isOpenAccess) {
            this.buttonCreator.createGetButton(
              onClick = {
                this.viewModel.borrowMaybeAuthenticated()
              }
            )
          } else {
            this.buttonCreator.createDownloadButton(
              onClick = {
                this.viewModel.borrowMaybeAuthenticated()
              }
            )
          }
        )

        if (createPreviewButton) {
          this.buttons.addView(this.buttonCreator.createButtonSpace())
          this.buttons.addView(
            this.buttonCreator.createReadPreviewButton(
              bookFormat = parameters.feedEntry.probableFormat,
              onClick = {
                viewModel.openBookPreview(parameters.feedEntry)
              }
            )
          )
        }
      }
      is BookStatus.Loaned.LoanedDownloaded -> {
        // the book preview button can be ignored
        createPreviewButton = false

        when (val format = book.findPreferredFormat()) {
          is BookFormat.BookFormatPDF,
          is BookFormat.BookFormatEPUB -> {
            this.buttons.addView(
              this.buttonCreator.createReadButton(
                onClick = {
                  this.viewModel.openViewer(format)
                }
              )
            )
          }
          is BookFormat.BookFormatAudioBook -> {
            this.buttons.addView(
              this.buttonCreator.createListenButton(
                onClick = {
                  this.viewModel.openViewer(format)
                }
              )
            )
          }
          else -> {
            // do nothing
          }
        }
      }
    }

    val isRevocable = this.viewModel.bookCanBeRevoked && this.buildConfig.allowReturns()
    if (isRevocable) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = {
            this.viewModel.revokeMaybeAuthenticated()
          }
        )
      )
    } else if (this.viewModel.bookCanBeDeleted) {
      this.buttons.addView(this.buttonCreator.createButtonSpace())
      this.buttons.addView(
        this.buttonCreator.createRevokeLoanButton(
          onClick = {
            this.viewModel.delete()
          }
        )
      )
    } else if (!createPreviewButton) {
      // add spaces on both sides if there aren't any other buttons
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace(), 0)
      this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    }

    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.VISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusIdleText.text =
      CatalogBookAvailabilityStrings.statusString(this.resources, bookStatus)
  }

  private fun onBookStatusDownloading(
    bookStatus: BookStatus.Downloading,
  ) {
    /*
     * XXX: https://jira.nypl.org/browse/SIMPLY-3444
     *
     * Avoid creating a cancel button until we can reliably support cancellation for *all* books.
     * That is, when the Adobe DRM is dead and buried.
     */

    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createButtonSizedSpace())
    this.buttons.addView(
      this.buttonCreator.createCancelDownloadButton(
        onClick = {
          this.viewModel.cancelDownload()
        }
      )
    )
    this.buttons.addView(this.buttonCreator.createButtonSizedSpace())

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE

    val progressPercent = bookStatus.progressPercent?.toInt()
    if (progressPercent != null) {
      this.statusInProgressText.visibility = View.VISIBLE
      this.statusInProgressText.text = "$progressPercent%"
      this.statusInProgressBar.isIndeterminate = false
      this.statusInProgressBar.progress = progressPercent
    } else {
      this.statusInProgressText.visibility = View.GONE
      this.statusInProgressBar.isIndeterminate = true
      this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogDownloading))
      this.checkButtonViewCount()
    }
  }

  private fun onBookStatusDownloadWaitingForExternalAuthentication() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogLoginRequired))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onBookStatusDownloadExternalAuthenticationInProgress() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogLoginRequired))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onBookStatusRequestingDownload() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onBookStatusRequestingRevoke() {
    this.buttons.removeAllViews()
    this.buttons.addView(this.buttonCreator.createCenteredTextForButtons(R.string.catalogRequesting))
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.VISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.INVISIBLE
    this.statusInProgressText.visibility = View.GONE
    this.statusInProgressBar.isIndeterminate = true
  }

  private fun onBookStatusFailedDownload(
    bookStatus: BookStatus.FailedDownload,
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(
      this.buttonCreator.createDismissButton {
        this.viewModel.dismissBorrowError()
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createDetailsButton {
        this.viewModel.showError(bookStatus.result)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createRetryButton {
        this.viewModel.borrowMaybeAuthenticated()
      }
    )
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.VISIBLE
    this.statusFailedText.text = this.getString(R.string.catalogOperationFailed)
  }

  private fun onBookStatusFailedRevoke(
    bookStatus: BookStatus.FailedRevoke,
  ) {
    this.buttons.removeAllViews()
    this.buttons.addView(
      this.buttonCreator.createDismissButton {
        this.viewModel.dismissRevokeError()
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createDetailsButton {
        this.viewModel.showError(bookStatus.result)
      }
    )
    this.buttons.addView(this.buttonCreator.createButtonSpace())
    this.buttons.addView(
      this.buttonCreator.createRetryButton {
        this.viewModel.revokeMaybeAuthenticated()
      }
    )
    this.checkButtonViewCount()

    this.statusInProgress.visibility = View.INVISIBLE
    this.statusIdle.visibility = View.INVISIBLE
    this.statusFailed.visibility = View.VISIBLE
    this.statusFailedText.text = this.resources.getText(R.string.catalogOperationFailed)
  }

  private fun checkButtonViewCount() {
    Preconditions.checkState(
      this.buttons.childCount > 0,
      "At least one button must be present (existing ${this.buttons.childCount})"
    )
  }
}
