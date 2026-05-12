package org.nypl.simplified.ui.accounts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.ui.images.ImageLoaderType
import org.slf4j.LoggerFactory

/**
 * Adapter for showing a list of `AccountProviderDescription` items.
 *
 * Use [submitList] to add items to the adapter.
 */

class AccountProviderDescriptionListAdapter(
  private val imageLoader: ImageLoaderType,
  private val onItemClicked: (AccountProviderDescription) -> Unit
) : ListAdapter<AccountProviderDescription?, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

  companion object {
    private const val VIEW_TYPE_ACCOUNT = 0
    private const val VIEW_TYPE_GAP = 1

    /**
     * Callback for calculating the diff between two non-null items in a list.
     */

    val DIFF_CALLBACK =
      object : DiffUtil.ItemCallback<AccountProviderDescription?>() {
        override fun areItemsTheSame(
          oldItem: AccountProviderDescription,
          newItem: AccountProviderDescription
        ): Boolean {
          return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
          oldItem: AccountProviderDescription,
          newItem: AccountProviderDescription
        ): Boolean {
          return oldItem.title == newItem.title && oldItem.logoURI == newItem.logoURI
        }
      }
  }

  private val logger =
    LoggerFactory.getLogger(AccountProviderDescriptionListAdapter::class.java)

  override fun getItemViewType(
    position: Int
  ): Int {
    return if (
      currentList.isEmpty() ||
      currentList[position] != null
    ) {
      VIEW_TYPE_ACCOUNT
    } else {
      VIEW_TYPE_GAP
    }
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): RecyclerView.ViewHolder {
    val inflater = LayoutInflater.from(parent.context)

    return if (
      viewType == VIEW_TYPE_ACCOUNT
    ) {
      val item = inflater.inflate(R.layout.account_list_item, parent, false)

      AccountItemViewHolder(
        item,
        this.imageLoader,
        this.onItemClicked
      )
    } else {
      val gap = inflater.inflate(R.layout.account_list_gap, parent, false)
      return AccountItemGapViewHolder(gap)
    }
  }

  override fun onBindViewHolder(
    holder: RecyclerView.ViewHolder,
    position: Int
  ) {
    val item = getItem(position)
    item ?: return
    (holder as? AccountItemViewHolder)?.bind(item)
  }
}
