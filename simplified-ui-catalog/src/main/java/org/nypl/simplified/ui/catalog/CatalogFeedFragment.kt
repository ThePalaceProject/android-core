package org.nypl.simplified.ui.catalog

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.View.TEXT_ALIGNMENT_TEXT_END
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.setPadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.android.ktx.supportActionBar
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.feeds.api.FeedBooksSelection
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.feeds.api.FeedFacets
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.AccountPickerDialogFragment
import org.nypl.simplified.ui.catalog.CatalogFeedOwnership.CollectedFromAccounts
import org.nypl.simplified.ui.catalog.CatalogFeedOwnership.OwnedByAccount
import org.nypl.simplified.ui.catalog.CatalogFeedState.CatalogFeedAgeGate
import org.nypl.simplified.ui.catalog.CatalogFeedState.CatalogFeedLoadFailed
import org.nypl.simplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded
import org.nypl.simplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedEmpty
import org.nypl.simplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedNavigation
import org.nypl.simplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithGroups
import org.nypl.simplified.ui.catalog.CatalogFeedState.CatalogFeedLoaded.CatalogFeedWithoutGroups
import org.nypl.simplified.ui.catalog.CatalogFeedState.CatalogFeedLoading
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.neutrality.NeutralToolbar
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.slf4j.LoggerFactory

/**
 * A fragment displaying an OPDS feed.
 */

class CatalogFeedFragment : Fragment(R.layout.feed), AgeGateDialog.BirthYearSelectedListener {

  companion object {

    private const val PARAMETERS_ID =
      "org.nypl.simplified.ui.catalog.CatalogFragmentFeed.parameters"

    private val AGE_GATE_DIALOG_TAG =
      AgeGateDialog::class.java.simpleName

    /**
     * Create a catalog feed fragment for the given parameters.
     */

    fun create(parameters: CatalogFeedArguments): CatalogFeedFragment {
      val fragment = CatalogFeedFragment()
      fragment.arguments = bundleOf(PARAMETERS_ID to parameters)
      return fragment
    }
  }

  private val logger = LoggerFactory.getLogger(CatalogFeedFragment::class.java)

  private val parameters: CatalogFeedArguments by lazy {
    this.requireArguments()[PARAMETERS_ID] as CatalogFeedArguments
  }

  private val services =
    Services.serviceDirectory()

  private val listener: FragmentListenerType<CatalogFeedEvent> by fragmentListeners()

  private val borrowViewModel: CatalogBorrowViewModel by viewModels(
    factoryProducer = {
      CatalogBorrowViewModelFactory(services)
    }
  )

  private val viewModel: CatalogFeedViewModel by viewModels(
    factoryProducer = {
      CatalogFeedViewModelFactory(
        application = this.requireActivity().application,
        services = Services.serviceDirectory(),
        borrowViewModel = borrowViewModel,
        feedArguments = this.parameters,
        listener = this.listener
      )
    }
  )

  private val bookCovers =
    services.requireService(BookCoverProviderType::class.java)
  private val screenInformation =
    services.requireService(ScreenSizeInformationType::class.java)
  private val configurationService =
    services.requireService(BuildConfigurationServiceType::class.java)
  private val profilesController =
    services.requireService(ProfilesControllerType::class.java)
  private val imageLoader =
    services.requireService(ImageLoaderType::class.java)

  private lateinit var buttonCreator: CatalogButtons
  private lateinit var feedContent: ViewGroup
  private lateinit var feedError: ViewGroup
  private lateinit var feedErrorDetails: Button
  private lateinit var feedErrorRetry: Button
  private lateinit var feedLoading: ViewGroup
  private lateinit var feedNavigation: ViewGroup
  private lateinit var feedContentFacets: LinearLayout
  private lateinit var feedContentFacetsScroll: ViewGroup
  private lateinit var feedContentHeader: ViewGroup
  private lateinit var feedContentLogoHeader: ViewGroup
  private lateinit var feedContentLogoImage: ImageView
  private lateinit var feedContentLogoText: TextView
  private lateinit var feedEmptyMessage: TextView
  private lateinit var feedContentTabs: RadioGroup
  private lateinit var feedContentRefresh: SwipeRefreshLayout
  private lateinit var feedWithGroupsAdapter: CatalogFeedWithGroupsAdapter
  private lateinit var feedWithGroupsList: RecyclerView
  private lateinit var feedWithoutGroupsAdapter: CatalogPagedAdapter
  private lateinit var feedWithoutGroupsList: RecyclerView
  private lateinit var feedWithoutGroupsScrollListener: RecyclerView.OnScrollListener
  private lateinit var toolbar: NeutralToolbar

  private var ageGateDialog: DialogFragment? = null
  private val feedWithGroupsData: MutableList<FeedGroup> = mutableListOf()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)

    this.ageGateDialog =
      childFragmentManager.findFragmentByTag(AGE_GATE_DIALOG_TAG) as? DialogFragment
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbar =
      view.rootView.findViewWithTag(NeutralToolbar.neutralToolbarName)

    this.viewModel.stateLive.observe(this.viewLifecycleOwner, this::reconfigureUI)

    this.buttonCreator =
      CatalogButtons(this.requireContext(), this.screenInformation)

    this.feedError =
      view.findViewById(R.id.feedError)
    this.feedLoading =
      view.findViewById(R.id.feedLoading)
    this.feedNavigation =
      view.findViewById(R.id.feedNavigation)
    this.feedContent =
      view.findViewById(R.id.feedContent)

    this.feedContentHeader =
      view.findViewById(R.id.feedContentHeader)
    this.feedContentRefresh =
      view.findViewById(R.id.feedContentRefresh)
    this.feedContentFacetsScroll =
      this.feedContentHeader.findViewById(R.id.feedHeaderFacetsScroll)
    this.feedEmptyMessage =
      this.feedContent.findViewById(R.id.feedEmptyMessage)

    if (parameters is CatalogFeedArguments.CatalogFeedArgumentsLocalBooks) {
      this.feedEmptyMessage.setText(
        if (
          (parameters as CatalogFeedArguments.CatalogFeedArgumentsLocalBooks).selection ==
          FeedBooksSelection.BOOKS_FEED_HOLDS
        ) {
          R.string.feedWithGroupsEmptyHolds
        } else {
          R.string.feedWithGroupsEmptyLoaned
        }
      )
    }

    this.feedContentFacets =
      this.feedContentHeader.findViewById(R.id.feedHeaderFacets)
    this.feedContentTabs =
      this.feedContentHeader.findViewById(R.id.feedHeaderTabs)

    this.feedContentLogoHeader =
      this.feedContent.findViewById(R.id.feedContentLogoHeader)
    this.feedContentLogoImage =
      this.feedContent.findViewById(R.id.feedLibraryLogo)
    this.feedContentLogoText =
      this.feedContent.findViewById(R.id.feedLibraryText)

    this.feedContentRefresh =
      this.feedContent.findViewById(R.id.feedContentRefresh)

    this.feedWithGroupsList = this.feedContent.findViewById(R.id.feedWithGroupsList)
    this.feedWithGroupsList.setHasFixedSize(true)
    this.feedWithGroupsList.setItemViewCacheSize(32)
    this.feedWithGroupsList.layoutManager = LinearLayoutManager(this.context)
    (this.feedWithGroupsList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    this.feedWithGroupsList.addItemDecoration(
      CatalogFeedWithGroupsDecorator(this.screenInformation.dpToPixels(16).toInt())
    )

    this.feedWithGroupsAdapter =
      CatalogFeedWithGroupsAdapter(
        groups = this.feedWithGroupsData,
        coverLoader = this.bookCovers,
        onFeedSelected = this.viewModel::openFeed,
        onBookSelected = this.viewModel::openBookDetail
      )
    this.feedWithGroupsList.adapter = this.feedWithGroupsAdapter
    this.feedWithoutGroupsList = this.feedContent.findViewById(R.id.feedWithoutGroupsList)
    this.feedWithoutGroupsList.setHasFixedSize(true)
    this.feedWithoutGroupsList.setItemViewCacheSize(32)
    this.feedWithoutGroupsList.layoutManager = LinearLayoutManager(this.context)
    (this.feedWithoutGroupsList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

    this.feedErrorRetry =
      this.feedError.findViewById(R.id.feedErrorRetry)
    this.feedErrorDetails =
      this.feedError.findViewById(R.id.feedErrorDetails)

    this.feedContent.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE

    this.feedContentRefresh.setOnRefreshListener {
      this.refresh()
      this.feedContentRefresh.isRefreshing = false
    }

    this.feedContentLogoHeader.setOnClickListener {
      this.openLogoLink()
    }
  }

  private fun openLogoLink() {
    this.logger.debug("logo: attempting to open link")

    try {
      val accountProvider = this.viewModel.accountProvider
      if (accountProvider != null) {
        val alternate = accountProvider.alternateURI
        if (alternate != null) {
          val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(alternate.toString()))
          this.startActivity(browserIntent)
        } else {
          this.logger.debug("logo: no alternate link")
        }
      } else {
        this.logger.debug("logo: account provider was null")
      }
    } catch (e: Exception) {
      this.logger.error("logo: unable to handle alternate link: ", e)
    }
  }

  override fun onStart() {
    super.onStart()

    this.feedWithoutGroupsScrollListener = CatalogScrollListener(this.bookCovers)
    this.feedWithoutGroupsList.addOnScrollListener(this.feedWithoutGroupsScrollListener)
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    inflater.inflate(R.menu.catalog, menu)

    val search = menu.findItem(R.id.catalogMenuActionSearch)
    val searchView = search.actionView as SearchView

    searchView.imeOptions = EditorInfo.IME_ACTION_DONE
    searchView.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    searchView.queryHint = getString(R.string.catalogSearch)
    searchView.maxWidth = toolbar.getAvailableWidthForSearchView()

    val currentQuery = when (parameters) {
      is CatalogFeedArguments.CatalogFeedArgumentsLocalBooks -> {
        (parameters as CatalogFeedArguments.CatalogFeedArgumentsLocalBooks).searchTerms.orEmpty()
      }
      is CatalogFeedArguments.CatalogFeedArgumentsRemote -> {
        val uri =
          Uri.parse(
            (parameters as CatalogFeedArguments.CatalogFeedArgumentsRemote).feedURI.toString()
          )
        uri.getQueryParameter("q").orEmpty()
      }
    }

    searchView.setQuery(currentQuery, false)

    // if there's no query, the search should be iconified, i.e., collapsed
    searchView.isIconified = currentQuery.isBlank()

    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
      override fun onQueryTextSubmit(query: String): Boolean {
        val viewModel = this@CatalogFeedFragment.viewModel
        viewModel.stateLive.value?.search?.let { search ->
          viewModel.performSearch(search, query)
        }
        searchView.clearFocus()
        return true
      }

      override fun onQueryTextChange(newText: String): Boolean {
        return true
      }
    })

    searchView.clearFocus()
  }

  override fun onPrepareOptionsMenu(menu: Menu) {
    super.onPrepareOptionsMenu(menu)

    // Necessary to reconfigure the Toolbar here due to the "Switch Account" action.
    this.configureToolbar()
  }

  private fun refresh() {
    this.viewModel.syncAccounts()
    this.viewModel.reloadFeed()
  }

  private fun reconfigureUI(feedState: CatalogFeedState) {
    return when (feedState) {
      is CatalogFeedAgeGate ->
        this.onCatalogFeedAgeGate(feedState)
      is CatalogFeedLoading ->
        this.onCatalogFeedLoading(feedState)
      is CatalogFeedWithGroups ->
        this.onCatalogFeedWithGroups(feedState)
      is CatalogFeedWithoutGroups ->
        this.onCatalogFeedWithoutGroups(feedState)
      is CatalogFeedNavigation ->
        this.onCatalogFeedNavigation(feedState)
      is CatalogFeedLoadFailed ->
        this.onCatalogFeedLoadFailed(feedState)
      is CatalogFeedEmpty ->
        this.onCatalogFeedEmpty(feedState)
    }
  }

  override fun onStop() {
    super.onStop()

    /*
     * We aggressively unset adapters here in order to try to encourage prompt unsubscription
     * of views from the book registry.
     */

    this.feedWithoutGroupsList.removeOnScrollListener(this.feedWithoutGroupsScrollListener)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    this.feedWithoutGroupsList.adapter = null
    this.feedWithGroupsList.adapter = null
  }

  private fun onCatalogFeedAgeGate(
    @Suppress("UNUSED_PARAMETER") feedState: CatalogFeedAgeGate
  ) {
    this.openAgeGateDialog()
    this.feedContent.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE

    this.configureToolbar()
  }

  private fun onCatalogFeedEmpty(
    @Suppress("UNUSED_PARAMETER") feedState: CatalogFeedEmpty
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.VISIBLE
    this.feedEmptyMessage.visibility = View.VISIBLE
    this.feedContentHeader.visibility = View.GONE
    this.feedWithGroupsList.visibility = View.INVISIBLE
    this.feedWithoutGroupsList.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE

    this.configureLogoHeader(feedState)
    this.configureToolbar()
  }

  private fun onCatalogFeedLoading(
    @Suppress("UNUSED_PARAMETER") feedState: CatalogFeedLoading
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.VISIBLE
    this.feedNavigation.visibility = View.INVISIBLE

    this.configureToolbar()
  }

  private fun onCatalogFeedNavigation(
    @Suppress("UNUSED_PARAMETER") feedState: CatalogFeedNavigation
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.VISIBLE

    this.configureLogoHeader(feedState)
    this.configureToolbar()
  }

  private fun onCatalogFeedWithoutGroups(
    feedState: CatalogFeedWithoutGroups
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.VISIBLE
    this.feedEmptyMessage.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE
    this.feedWithGroupsList.visibility = View.INVISIBLE
    this.feedWithoutGroupsList.visibility = View.VISIBLE

    this.configureToolbar()
    this.configureFacets(
      facetsByGroup = feedState.facetsByGroup,
      sortFacets = false
    )
    this.configureLogoHeader(feedState)

    this.feedWithoutGroupsAdapter =
      CatalogPagedAdapter(
        context = requireActivity(),
        listener = this.viewModel,
        buttonCreator = this.buttonCreator,
        bookCovers = this.bookCovers,
        profilesController = this.profilesController
      )

    this.feedWithoutGroupsList.adapter = this.feedWithoutGroupsAdapter
    feedState.entries.observe(this) { newPagedList ->
      this.logger.debug("received paged list ({} elements)", newPagedList.size)
      this.feedWithoutGroupsAdapter.submitList(newPagedList)
    }
  }

  private fun onCatalogFeedWithGroups(
    feedState: CatalogFeedWithGroups
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.VISIBLE
    this.feedEmptyMessage.visibility = View.INVISIBLE
    this.feedError.visibility = View.INVISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE
    this.feedWithGroupsList.visibility = View.VISIBLE
    this.feedWithoutGroupsList.visibility = View.INVISIBLE

    this.configureToolbar()
    this.configureFacets(
      facetsByGroup = feedState.feed.facetsByGroup,
      sortFacets = true
    )
    this.configureLogoHeader(feedState)

    this.feedWithGroupsData.clear()
    this.feedWithGroupsData.addAll(feedState.feed.feedGroupsInOrder)
    this.feedWithGroupsAdapter.notifyDataSetChanged()
  }

  private fun configureLogoHeader(feedState: CatalogFeedLoaded) {
    fun loadImageAndText(accountId: AccountID) {
      try {
        val account =
          this.profilesController.profileCurrent()
            .account(accountId)

        ImageAccountIcons.loadAccountLogoIntoView(
          loader = this.imageLoader.loader,
          account = account.provider.toDescription(),
          defaultIcon = org.nypl.simplified.ui.accounts.R.drawable.account_default,
          iconView = feedContentLogoImage
        )

        feedContentLogoText.text = account.provider.displayName
      } catch (e: Exception) {
        this.logger.debug("error configuring header: ", e)
      }
    }

    when (feedState) {
      is CatalogFeedNavigation -> {
        // do nothing
      }
      else -> {
        when (val ownership = feedState.arguments.ownership) {
          CollectedFromAccounts -> {
            this.feedContentLogoHeader.visibility = View.GONE
          }
          is OwnedByAccount -> {
            this.feedContentLogoHeader.visibility = View.VISIBLE
            loadImageAndText(accountId = ownership.accountId)
          }
        }
      }
    }
  }

  private fun onCatalogFeedLoadFailed(
    feedState: CatalogFeedLoadFailed
  ) {
    this.dismissAgeGateDialog()
    this.feedContent.visibility = View.INVISIBLE
    this.feedError.visibility = View.VISIBLE
    this.feedLoading.visibility = View.INVISIBLE
    this.feedNavigation.visibility = View.INVISIBLE

    this.configureToolbar()

    this.feedErrorRetry.isEnabled = true
    this.feedErrorRetry.setOnClickListener { button ->
      button.isEnabled = false
      this.viewModel.reloadFeed()
    }

    this.feedErrorDetails.isEnabled = true
    this.feedErrorDetails.setOnClickListener {
      this.viewModel.showFeedErrorDetails(feedState.failure)
    }
  }

  private fun configureToolbar() {
    try {
      this.toolbar.title = this.viewModel.title()
      val actionBar = this.supportActionBar ?: return
      actionBar.show()

      /*
       * If we're not at the root of a feed, then display a back arrow in the toolbar.
       */

      if (!this.viewModel.isAccountCatalogRoot()) {
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setHomeActionContentDescription(null)
        this.toolbar.setLogoOnClickListener {
          this.viewModel.goUpwards()
        }
        return
      }

      /*
       * If we're at the root of a feed and the app is configured such that the user should
       * be allowed to change accounts, then display the current account's logo in the toolbar.
       */

      if (this.configurationService.showChangeAccountsUi) {
        actionBar.setHomeActionContentDescription(R.string.catalogAccounts)
        actionBar.setLogo(this.configurationService.brandingAppIcon)
        this.toolbar.setLogoOnClickListener {
          this.openAccountPickerDialog()
        }
        return
      }

      /*
       * Otherwise, show nothing.
       */

      actionBar.setDisplayHomeAsUpEnabled(false)
      actionBar.setLogo(null)

      this.toolbar.setLogoOnClickListener {
        // Do nothing
      }
    } catch (e: Exception) {
      // Nothing to do
    }
  }

  private fun openAccountPickerDialog() {
    return when (val ownership = this.parameters.ownership) {
      is OwnedByAccount -> {
        val dialog =
          AccountPickerDialogFragment.create(
            currentId = ownership.accountId,
            showAddAccount = this.configurationService.allowAccountsAccess
          )
        dialog.show(parentFragmentManager, dialog.tag)
      }
      CollectedFromAccounts -> {
        throw IllegalStateException("Can't switch account from collected feed!")
      }
    }
  }

  @SuppressLint("InflateParams")
  private fun openSearchDialog(
    context: Context,
    search: FeedSearch
  ) {
    val view = LayoutInflater.from(context).inflate(R.layout.search_dialog, null)
    val searchView = view.findViewById<TextView>(R.id.searchDialogText)!!

    val builder = AlertDialog.Builder(context).apply {
      setPositiveButton(R.string.catalogSearch) { dialog, _ ->
        val query = searchView.text.toString().trim()
        this@CatalogFeedFragment.viewModel.performSearch(search, query)
        dialog.dismiss()
      }
      setNegativeButton(R.string.cancel) { dialog, _ ->
        dialog.dismiss()
      }
      setView(view)
    }

    val dialog = builder.create()
    searchView.setOnEditorActionListener { _, actionId, _ ->
      return@setOnEditorActionListener when (actionId) {
        EditorInfo.IME_ACTION_SEARCH -> {
          val query = searchView.text.toString().trim()
          this@CatalogFeedFragment.viewModel.performSearch(search, query)
          dialog.dismiss()
          true
        }
        else -> false
      }
    }
    dialog.show()

    // disabling the positive button can only be called after the dialog is shown
    val dialogPositiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
    dialogPositiveButton.isEnabled = false
    searchView.doAfterTextChanged { text ->
      dialogPositiveButton.isEnabled = !text.isNullOrBlank()
    }
  }

  private fun configureFacets(
    facetsByGroup: Map<String, List<FeedFacet>>,
    sortFacets: Boolean
  ) {
    /*
     * If the facet groups are empty, hide the header entirely.
     */

    if (facetsByGroup.isEmpty()) {
      feedContentHeader.visibility = View.GONE
      return
    }

    /*
     * If one of the groups is an entry point, display it as a set of tabs. Otherwise, hide
     * the tab layout entirely.
     */

    this.configureFacetTabs(FeedFacets.findEntryPointFacetGroup(facetsByGroup), feedContentTabs)

    /*
     * Otherwise, for each remaining non-entrypoint facet group, show a drop-down menu allowing
     * the selection of individual facets. If there are no remaining groups, hide the button
     * bar.
     */

    val remainingGroups = facetsByGroup
      .filter { entry ->
        /*
         * SIMPLY-2923: Hide the 'Collection' Facet until approved by UX.
         */
        entry.key != "Collection"
      }
      .filter { entry ->
        !FeedFacets.facetGroupIsEntryPointTyped(entry.value)
      }

    if (remainingGroups.isEmpty()) {
      feedContentFacetsScroll.visibility = View.GONE
      return
    }

    val buttonLayoutParams =
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

    val textLayoutParams =
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )

    textLayoutParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL

    val spacerLayoutParams =
      LinearLayout.LayoutParams(
        this.screenInformation.dpToPixels(8).toInt(),
        LinearLayout.LayoutParams.MATCH_PARENT
      )

    val sortedNames = if (sortFacets) {
      remainingGroups.keys.sorted()
    } else {
      remainingGroups.keys
    }
    val context = this.requireContext()

    feedContentFacets.removeAllViews()
    sortedNames.forEach { groupName ->
      val group = remainingGroups.getValue(groupName)
      if (FeedFacets.facetGroupIsEntryPointTyped(group)) {
        return@forEach
      }

      val button = AppCompatButton(context)
      val buttonLabel = AppCompatTextView(context)
      val spaceStart = Space(context)
      val spaceMiddle = Space(context)
      val spaceEnd = Space(context)

      val active =
        group.find { facet -> facet.isActive }
          ?: group.firstOrNull()

      button.id = View.generateViewId()
      button.layoutParams = buttonLayoutParams
      button.text = active?.title
      button.ellipsize = TextUtils.TruncateAt.END
      button.setOnClickListener {
        this.showFacetSelectDialog(groupName, group)
      }

      spaceStart.layoutParams = spacerLayoutParams
      spaceMiddle.layoutParams = spacerLayoutParams
      spaceEnd.layoutParams = spacerLayoutParams

      buttonLabel.layoutParams = textLayoutParams
      buttonLabel.text = "$groupName: "
      buttonLabel.labelFor = button.id
      buttonLabel.maxLines = 1
      buttonLabel.ellipsize = TextUtils.TruncateAt.END
      buttonLabel.textAlignment = TEXT_ALIGNMENT_TEXT_END
      buttonLabel.gravity = Gravity.END or Gravity.CENTER_VERTICAL

      feedContentFacets.addView(spaceStart)
      feedContentFacets.addView(buttonLabel)
      feedContentFacets.addView(spaceMiddle)
      feedContentFacets.addView(button)
      feedContentFacets.addView(spaceEnd)
    }

    feedContentFacetsScroll.scrollTo(0, 0)
  }

  private fun configureFacetTabs(
    facetGroup: List<FeedFacet>?,
    facetTabs: RadioGroup
  ) {
    if (facetGroup == null) {
      facetTabs.visibility = View.GONE
      return
    }

    /*
     * Add a set of radio buttons to the view.
     */

    facetTabs.removeAllViews()
    val size = facetGroup.size
    for (index in 0 until size) {
      val facet = facetGroup[index]
      val button = RadioButton(this.requireContext())
      val buttonLayout =
        LinearLayout.LayoutParams(
          0,
          ViewGroup.LayoutParams.MATCH_PARENT,
          1.0f / size.toFloat()
        )

      button.layoutParams = buttonLayout
      button.gravity = Gravity.CENTER
      button.maxLines = 1
      button.ellipsize = TextUtils.TruncateAt.END

      /*
       * The buttons need unique IDs so that they can be addressed within the parent
       * radio group.
       */

      button.id = View.generateViewId()

      button.setBackgroundResource(R.drawable.catalog_facet_tab_button_background)
      button.setButtonDrawable(R.drawable.catalog_facet_tab_button_background)

      button.text = facet.title
      button.setTextColor(
        ContextCompat.getColor(this.requireContext(), R.color.simplified_button_text)
      )
      button.setOnClickListener {
        this.logger.debug("selected entry point facet: {}", facet.title)
        this.viewModel.openFacet(facet)
      }
      button.setPadding(0)
      facetTabs.addView(button)
    }

    /*
     * Uncheck all of the buttons, and then check the one that corresponds to the current
     * active facet.
     */

    facetTabs.clearCheck()

    for (index in 0 until size) {
      val facet = facetGroup[index]
      val button = facetTabs.getChildAt(index) as RadioButton

      if (facet.isActive) {
        this.logger.debug("active entry point facet: {}", facet.title)
        facetTabs.check(button.id)
      }
    }
  }

  private fun showFacetSelectDialog(
    groupName: String,
    group: List<FeedFacet>
  ) {
    val choices = group.sortedBy { it.title }
    val names = choices.map { it.title }.toTypedArray()
    val checkedItem = choices.indexOfFirst { it.isActive }

    // Build the dialog
    val alertBuilder = AlertDialog.Builder(this.requireContext())
    alertBuilder.setTitle(groupName)
    alertBuilder.setSingleChoiceItems(names, checkedItem) { dialog, checked ->
      val selected = choices[checked]
      this.logger.debug("selected facet: {}", selected)
      this.viewModel.openFacet(selected)
      dialog.dismiss()
    }
    alertBuilder.create().show()
  }

  override fun onBirthYearSelected(isOver13: Boolean) {
    this.viewModel.updateBirthYear(isOver13)
  }

  private fun openAgeGateDialog() {
    if (this.ageGateDialog != null) {
      return
    }

    val ageGate = AgeGateDialog.create()
    ageGate.show(childFragmentManager, AGE_GATE_DIALOG_TAG)
    this.ageGateDialog = ageGate
  }

  private fun dismissAgeGateDialog() {
    this.ageGateDialog?.dismiss()
    this.ageGateDialog = null
  }
}
