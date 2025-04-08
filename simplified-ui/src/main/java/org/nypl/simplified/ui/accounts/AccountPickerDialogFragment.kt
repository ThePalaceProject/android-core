package org.nypl.simplified.ui.accounts

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.accounts.AccountPickerAdapter.OnAccountClickListener
import org.nypl.simplified.ui.catalog.CatalogOPDSClients
import org.nypl.simplified.ui.catalog.CatalogPart
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType
import org.slf4j.LoggerFactory

/**
 * Present a dialog that shows a list of all active accounts for the current profile.
 */

@Deprecated("Accounts will be chosen via the settings screen soon.")
class AccountPickerDialogFragment : BottomSheetDialogFragment(), OnAccountClickListener {

  private val logger =
    LoggerFactory.getLogger(AccountPickerDialogFragment::class.java)

  private var catalogPart: CatalogPart = CatalogPart.CATALOG
  private lateinit var recyclerView: RecyclerView
  private lateinit var imageLoader: ImageLoaderType
  private lateinit var profilesController: ProfilesControllerType
  private lateinit var accounts: List<AccountType>

  companion object {
    private const val ARG_CURRENT_ID = "org.nypl.simplified.ui.accounts.CURRENT_ID"
    private const val ARG_ADD_ACCOUNT = "org.nypl.simplified.ui.accounts.ADD_ACCOUNT"
    private const val ARG_CATALOG_PART = "org.nypl.simplified.ui.accounts.CATALOG_PART"

    fun create(
      currentId: AccountID,
      catalogPart: CatalogPart,
      showAddAccount: Boolean
    ): AccountPickerDialogFragment {
      return AccountPickerDialogFragment().apply {
        this.arguments = Bundle().apply {
          this.putSerializable(this@Companion.ARG_CATALOG_PART, catalogPart)
          this.putSerializable(this@Companion.ARG_CURRENT_ID, currentId)
          this.putBoolean(this@Companion.ARG_ADD_ACCOUNT, showAddAccount)
        }
      }
    }
  }

  override fun onCreate(
    savedInstanceState: Bundle?
  ) {
    super.onCreate(savedInstanceState)

    val services =
      Services.serviceDirectory()
    this.imageLoader =
      services.requireService(ImageLoaderType::class.java)
    this.profilesController =
      services.requireService(ProfilesControllerType::class.java)

    val accountsMap =
      this.profilesController.profileCurrent().accounts()

    this.accounts =
      accountsMap.values.toList()
        .sortedWith(AccountComparator())
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.account_picker, container, false)
  }

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    val arguments =
      this.requireArguments()
    val currentId =
      arguments.getSerializable(ARG_CURRENT_ID) as AccountID
    val showAddAccount =
      arguments.getBoolean(ARG_ADD_ACCOUNT, false)
    this.catalogPart =
      arguments.getSerializable(ARG_CATALOG_PART) as CatalogPart

    this.recyclerView = view.findViewById(R.id.recyclerView)
    this.recyclerView.apply {
      this.setHasFixedSize(true)
      this.layoutManager =
        LinearLayoutManager(this@AccountPickerDialogFragment.requireContext())
      this.adapter =
        AccountPickerAdapter(
          this@AccountPickerDialogFragment.accounts,
          currentId,
          this@AccountPickerDialogFragment.imageLoader,
          showAddAccount,
          this@AccountPickerDialogFragment
        )
    }
  }

  override fun onAccountClick(
    account: AccountType
  ) {
    this.logger.debug("selected account id={}, name={}", account.id, account.provider.displayName)

    // Note: In the future consider refactoring this dialog to return a result via
    //       setFragmentResultListener to decouple it from the profiles logic.
    val profile =
      this.profilesController.profileCurrent()

    val newPreferences = profile
      .preferences()
      .copy(mostRecentAccount = account.id)
    this.profilesController.profileUpdate { it.copy(preferences = newPreferences) }

    val services =
      Services.serviceDirectory()
    val opdsClients =
      services.requireService(CatalogOPDSClients::class.java)

    opdsClients.goToRootFeedFor(
      profiles = this.profilesController,
      catalogPart = this.catalogPart,
      account = account
    )
    this.dismiss()
  }

  override fun onAddAccountClick() {
    TODO()
    this.dismiss()
  }

  override fun onCancelClick() {
    this.dismiss()
  }
}

class AccountPickerViewHolder(
  view: View,
  private val imageLoader: ImageLoaderType,
  private val listener: OnAccountClickListener
) : RecyclerView.ViewHolder(view) {
  private val titleView: TextView = view.findViewById(R.id.accountTitle)
  private val activeView: View = view.findViewById(R.id.activeAccount)
  private val iconView: ImageView = view.findViewById(R.id.accountIcon)

  var account: AccountType? = null

  init {
    view.setOnClickListener {
      this.account?.let { this.listener.onAccountClick(it) }
    }
  }

  fun bind(account: AccountType, isCurrent: Boolean) {
    this.account = account

    this.titleView.text = account.provider.displayName

    if (isCurrent) {
      this.titleView.typeface = Typeface.DEFAULT_BOLD
      this.activeView.visibility = View.VISIBLE
    } else {
      this.titleView.typeface = Typeface.DEFAULT
      this.activeView.visibility = View.GONE
    }

    ImageAccountIcons.loadAccountLogoIntoView(
      loader = this.imageLoader.loader,
      account = account.provider.toDescription(),
      defaultIcon = R.drawable.account_default,
      iconView = this.iconView
    )
  }
}

class AddAccountViewHolder(
  view: View,
  private val listener: OnAccountClickListener
) : RecyclerView.ViewHolder(view) {

  init {
    val titleView: TextView = view.findViewById(R.id.accountTitle)
    titleView.setText(R.string.accountAdd)

    val iconView: ImageView = view.findViewById(R.id.accountIcon)
    iconView.colorFilter =
      PorterDuffColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
    iconView.setImageResource(R.drawable.ic_add)

    view.setOnClickListener {
      this.listener.onAddAccountClick()
    }
  }
}

class CancelViewHolder(
  view: View,
  private val listener: OnAccountClickListener
) : RecyclerView.ViewHolder(view) {

  init {
    val titleView: TextView = view.findViewById(R.id.accountTitle)
    titleView.setText(R.string.accountCancel)

    val iconView: ImageView = view.findViewById(R.id.accountIcon)
    iconView.visibility = View.GONE

    view.setOnClickListener {
      this.listener.onCancelClick()
    }
  }
}

class AccountPickerAdapter(
  private val accounts: List<AccountType>,
  private val currentId: AccountID,
  private val imageLoader: ImageLoaderType,
  private val showAddAccount: Boolean,
  private val listener: OnAccountClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

  companion object {
    private const val LIST_ITEM = 1
    private const val LIST_ADD_ACCOUNT = 2
    private const val LIST_CANCEL = 3
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val view = inflater.inflate(R.layout.account_picker_item, parent, false)
    return when (viewType) {
      LIST_CANCEL -> CancelViewHolder(
        view,
        this.listener
      )

      LIST_ADD_ACCOUNT -> AddAccountViewHolder(
        view,
        this.listener
      )

      else -> AccountPickerViewHolder(view, this.imageLoader, this.listener)
    }
  }

  override fun getItemCount() = this.accounts.size + if (this.showAddAccount) {
    2 // Show the 'add account' and 'cancel' options
  } else {
    1 // Show 'cancel' option
  }

  override fun getItemViewType(position: Int) = when (position) {
    this.accounts.size + 1 -> LIST_CANCEL
    this.accounts.size ->
      if (this.showAddAccount) {
        LIST_ADD_ACCOUNT
      } else {
        LIST_CANCEL
      }

    else -> LIST_ITEM
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    when (holder.itemViewType) {
      LIST_ITEM -> {
        val item = this.accounts[position]
        (holder as AccountPickerViewHolder).bind(item, item.id == this.currentId)
      }
    }
  }

  @Deprecated(message = "Make direct calls to a model.")
  interface OnAccountClickListener {
    fun onAccountClick(account: AccountType)
    fun onAddAccountClick()
    fun onCancelClick()
  }
}
