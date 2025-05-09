package org.nypl.simplified.ui.accounts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType

/**
 * An adapter for a list of accounts.
 */

class AccountListAdapter(
  private val imageLoader: ImageLoaderType,
  private val onItemClicked: (AccountType) -> Unit,
  private val onItemDeleteClicked: (AccountType) -> Unit
) : ListAdapter<AccountType, AccountListAdapter.AccountViewHolder>(AccountDiff) {

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): AccountViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val itemView = inflater.inflate(R.layout.account_list_item_old, parent, false)
    return AccountViewHolder(
      itemView,
      imageLoader,
      onItemClicked,
      onItemDeleteClicked
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
    private val imageLoader: ImageLoaderType,
    private val onItemClicked: (AccountType) -> Unit,
    private val onItemDeleteClicked: (AccountType) -> Unit
  ) : RecyclerView.ViewHolder(itemView) {
    private val accountIcon =
      itemView.findViewById<ImageView>(R.id.accountIcon)
    private val accountTitleView =
      itemView.findViewById<TextView>(R.id.accountTitle)
    private val accountCaptionView =
      itemView.findViewById<TextView>(R.id.accountCaption)
    private val deleteIcon =
      itemView.findViewById<View>(R.id.accountDeleteButton)

    private var accountItem: AccountType? = null

    init {
      this.itemView.setOnClickListener {
        this.accountItem?.let { account ->
          this.onItemClicked.invoke(account)
        }
      }

      this.deleteIcon.visibility = View.VISIBLE
      this.deleteIcon.setOnClickListener {
        this.accountItem?.let { account ->
          this.onItemDeleteClicked.invoke(account)
        }
      }
    }

    fun bind(item: AccountType) {
      this.accountTitleView.text = item.provider.displayName
      this.accountCaptionView.text = item.provider.subtitle

      item.preferences.catalogURIOverride?.let { uri ->
        this.accountCaptionView.text = uri.toString()
      }

      this.accountCaptionView.visibility =
        if (this.accountCaptionView.text.isNotEmpty()) {
          View.VISIBLE
        } else {
          View.GONE
        }

      this.imageLoader.loader.cancelRequest(this.accountIcon)
      ImageAccountIcons.loadAccountLogoIntoView(
        loader = this.imageLoader.loader,
        account = item.provider.toDescription(),
        defaultIcon = R.drawable.account_default,
        iconView = this.accountIcon
      )
      this.accountItem = item
    }
  }

  object AccountDiff : DiffUtil.ItemCallback<AccountType>() {
    override fun areItemsTheSame(oldItem: AccountType, newItem: AccountType): Boolean {
      return oldItem.id.compareTo(newItem.id) == 0
    }

    override fun areContentsTheSame(oldItem: AccountType, newItem: AccountType): Boolean {
      return oldItem.provider.displayName == newItem.provider.displayName &&
        oldItem.provider.subtitle == newItem.provider.subtitle &&
        oldItem.preferences.catalogURIOverride == newItem.preferences.catalogURIOverride
    }
  }
}
