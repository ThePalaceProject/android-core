package org.nypl.simplified.ui.accounts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.images.ImageLoader2Type
import org.nypl.simplified.ui.settings.SettingsProfileEvents

/**
 * An adapter for a list of accounts.
 */

class AccountListAdapter(
  private val imageLoader: ImageLoader2Type,
  private val onItemAccountSwitch: (AccountType) -> Unit,
  private val onItemAccountDetailsRequested: (AccountType) -> Unit,
  private val onItemAccountDeleteRequested: (AccountType) -> Unit,
  private val onItemIsSelectedNow: (AccountType) -> Boolean,
) : ListAdapter<AccountType, AccountListAdapter.AccountViewHolder>(AccountDiff) {
  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): AccountViewHolder {
    val inflater =
      LayoutInflater.from(parent.context)
    val itemView =
      inflater.inflate(R.layout.account_list_item, parent, false)
    return AccountViewHolder(
      itemView,
      this.imageLoader,
      onItemAccountSwitch = this.onItemAccountSwitch,
      onItemAccountDetailsRequested = this.onItemAccountDetailsRequested,
      onItemAccountDeleteRequested = this.onItemAccountDeleteRequested,
      onItemIsSelectedNow = this.onItemIsSelectedNow
    )
  }

  override fun onBindViewHolder(
    holder: AccountViewHolder,
    position: Int
  ) {
    holder.bind(this.getItem(position))
  }

  class AccountViewHolder(
    itemView: View,
    private val imageLoader: ImageLoader2Type,
    private val onItemAccountSwitch: (AccountType) -> Unit,
    private val onItemAccountDetailsRequested: (AccountType) -> Unit,
    private val onItemAccountDeleteRequested: (AccountType) -> Unit,
    private val onItemIsSelectedNow: (AccountType) -> Boolean,
  ) : RecyclerView.ViewHolder(itemView) {
    private val accountIcon =
      itemView.findViewById<ImageView>(R.id.accountItemIcon)
    private val accountRadio =
      itemView.findViewById<RadioButton>(R.id.accountItemRadio)
    private val accountTitleView =
      itemView.findViewById<TextView>(R.id.accountItemTitle)
    private val accountCaptionView =
      itemView.findViewById<TextView>(R.id.accountItemCaption)
    private val accountItemSelect =
      itemView.findViewById<ViewGroup>(R.id.accountItemSelect)
    private val accountItemDetails =
      itemView.findViewById<ViewGroup>(R.id.accountItemDetails)

    private var accountItem: AccountType? = null

    init {
      this.accountItemSelect.setOnClickListener {
        this.accountItem?.let { account ->
          this.onItemAccountSwitch.invoke(account)
        }
      }
      this.accountItemDetails.setOnClickListener {
        this.accountItem?.let { account ->
          this.onItemAccountDetailsRequested.invoke(account)
        }
      }
      this.accountItemSelect.setOnLongClickListener {
        this.accountItem?.let { account ->
          this.onItemAccountDeleteRequested.invoke(account)
        }
        true
      }
    }

    fun bind(item: AccountType) {
      this.accountTitleView.text =
        item.provider.displayName
      this.accountCaptionView.text =
        item.provider.description ?: item.provider.subtitle

      item.preferences.catalogURIOverride?.let { uri ->
        this.accountCaptionView.text = uri.toString()
      }

      this.accountCaptionView.visibility =
        if (this.accountCaptionView.text.isNotEmpty()) {
          View.VISIBLE
        } else {
          View.GONE
        }

      this.accountRadio.isChecked =
        this.onItemIsSelectedNow.invoke(item)

      this.imageLoader.loadAccountLogoIntoView(
        account = item.provider.toDescription(),
        defaultIcon = R.drawable.account_default,
        iconView = this.accountIcon
      )
      this.accountItem = item
    }
  }

  object AccountDiff : DiffUtil.ItemCallback<AccountType>() {
    override fun areItemsTheSame(
      oldItem: AccountType,
      newItem: AccountType
    ): Boolean = oldItem.id.compareTo(newItem.id) == 0

    override fun areContentsTheSame(
      oldItem: AccountType,
      newItem: AccountType
    ): Boolean =
      oldItem.provider.displayName == newItem.provider.displayName &&
        oldItem.provider.subtitle == newItem.provider.subtitle &&
        oldItem.preferences.catalogURIOverride == newItem.preferences.catalogURIOverride
  }
}
