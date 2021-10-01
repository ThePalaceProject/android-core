package org.nypl.simplified.ui.accounts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType
import org.slf4j.LoggerFactory

/**
 * Adapter for showing a list of `AccountProviderDescription` items.
 *
 * Use [submitList] to add items to the adapter.
 */

class FilterableAccountListAdapter(
  private val imageLoader: ImageLoaderType,
  private val onItemClicked: (AccountProviderDescription) -> Unit
) : ListAdapter<AccountProviderDescription?, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

  companion object {
    private const val VIEW_TYPE_ACCOUNT = 0
    private const val VIEW_TYPE_GAP = 1
  }

  private val logger =
    LoggerFactory.getLogger(FilterableAccountListAdapter::class.java)
  private var listCopy =
    mutableListOf<AccountProviderDescription?>()

  override fun getItemViewType(position: Int): Int {
    return if (
      currentList.isEmpty() ||
      currentList[position] != null
    ) {
      VIEW_TYPE_ACCOUNT
    } else {
      VIEW_TYPE_GAP
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
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
      return GapViewHolder(gap)
    }
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    val item = getItem(position)
    item ?: return
    (holder as? AccountItemViewHolder)?.bind(item)
  }

  override fun submitList(list: List<AccountProviderDescription?>?) {
    this.listCopy.clear()
    super.submitList(list)
  }

  override fun submitList(
    list: List<AccountProviderDescription?>?,
    commitCallback: Runnable?
  ) {
    this.listCopy.clear()
    super.submitList(list, commitCallback)
  }

  /** Returns true if the adapter is currently filtered. */

  fun isFiltered(): Boolean {
    return listCopy.isNotEmpty()
  }

  /**
   * Filter the original list of items. Each time this method is called the
   * original 'unfiltered' list is used as the base.
   */

  fun filterList(filter: (AccountProviderDescription?) -> Boolean) {
    if (this.listCopy.isEmpty()) {
      this.listCopy.addAll(this.currentList)
    }

    super.submitList(this.listCopy.filter(filter)) {
      this.logger.debug("{} matching items", this.currentList.size)
    }
  }

  /** Reset the filter and show the original list of items. */

  fun resetFilter() {
    if (!isFiltered()) return
    super.submitList(this.listCopy.toList()) {
      this.listCopy.clear()
    }
  }
}

/**
 * Holder for rendering an `AccountProviderDescription` as a list item.
 */

class AccountItemViewHolder(
  val itemView: View,
  private val imageLoader: ImageLoaderType,
  private val onItemClicked: (AccountProviderDescription) -> Unit
) : RecyclerView.ViewHolder(itemView) {
  private val accountIcon: ImageView =
    itemView.findViewById(R.id.accountIcon)
  private val accountTitleView =
    itemView.findViewById<TextView>(R.id.accountTitle)
  private val accountCaptionView =
    itemView.findViewById<TextView>(R.id.accountCaption)

  private var accountItem: AccountProviderDescription? = null

  init {
    this.itemView.setOnClickListener {
      this.accountItem?.let { account ->
        this.onItemClicked.invoke(account)
      }
    }
  }

  fun bind(item: AccountProviderDescription) {
    this.accountTitleView.text = item.title
    this.accountCaptionView.visibility =
      if (!item.description.isNullOrEmpty()) {
        this.accountCaptionView.text = item.description
        View.VISIBLE
      } else {
        View.GONE
      }
    ImageAccountIcons.loadAccountLogoIntoView(
      loader = this.imageLoader.loader,
      account = item,
      defaultIcon = R.drawable.account_default,
      iconView = this.accountIcon
    )
    this.accountItem = item
  }
}

class GapViewHolder(val itemView: View) : RecyclerView.ViewHolder(itemView)

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
      return oldItem.title == newItem.title
    }
  }
