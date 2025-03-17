package org.nypl.simplified.ui.settings

import android.content.res.ColorStateList
import android.graphics.PorterDuff
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

class SettingsAccountAdapter(
  private val isAccountSelected: (AccountType) -> Boolean,
  private val onSelectAccount: (AccountType) -> Unit,
  private val onOpenAccountSettings: (AccountType) -> Unit
) : ListAdapter<AccountType, RecyclerView.ViewHolder>(diffCallback) {

  companion object {
    private val diffCallback =
      object : DiffUtil.ItemCallback<AccountType>() {
        override fun areContentsTheSame(
          oldItem: AccountType,
          newItem: AccountType
        ): Boolean {
          return oldItem.id == newItem.id && oldItem.provider == newItem.provider
        }

        override fun areItemsTheSame(
          oldItem: AccountType,
          newItem: AccountType
        ): Boolean {
          return oldItem.id == newItem.id
        }
      }
  }

  override fun onBindViewHolder(
    holder: RecyclerView.ViewHolder,
    position: Int
  ) {
    (holder as? AccountViewHolder)?.bind(this.getItem(position))
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int
  ): RecyclerView.ViewHolder {
    val view =
      LayoutInflater.from(parent.context)
        .inflate(R.layout.settings_library_item, parent, false)

    return this.AccountViewHolder(view)
  }

  inner class AccountViewHolder(
    val view: View
  ) : RecyclerView.ViewHolder(view) {

    private val icon: ImageView =
      this.view.findViewById(R.id.settingsLibraryIcon)
    private val title: TextView =
      this.view.findViewById(R.id.settingsLibraryTitle)
    private val subtitle: TextView =
      this.view.findViewById(R.id.settingsLibrarySubtitle)
    private val selected: ImageView =
      this.view.findViewById(R.id.settingsLibrarySelected)

    fun bind(item: AccountType) {
      this.selected.imageTintMode = PorterDuff.Mode.MULTIPLY
      this.selected.contentDescription =
        this.view.context.getString(R.string.settingsSelectThisAccount, item.provider.displayName)

      if (isAccountSelected(item)) {
        this.selected.setImageResource(R.drawable.ic_settings_library_selected)
        this.selected.imageTintList =
          ColorStateList.valueOf(
            this.view.context.getColor(org.thepalaceproject.theme.core.R.color.PalaceGreen1))
      } else {
        this.selected.setImageResource(R.drawable.ic_settings_library_unselected)
        this.selected.imageTintList =
          ColorStateList.valueOf(
            this.view.context.getColor(org.thepalaceproject.theme.core.R.color.PalaceGrey1))
      }

      this.selected.setOnClickListener { onSelectAccount.invoke(item) }
      this.view.setOnClickListener { onOpenAccountSettings.invoke(item) }

      ImageAccountIcons.loadAccountLogoIntoView(
        loader = SettingsModel.imageLoader.loader,
        account = item.provider.toDescription(),
        defaultIcon = R.drawable.ic_settings_account,
        iconView = this.icon
      )
      this.title.text = item.provider.displayName
      this.subtitle.text = item.provider.description ?: item.provider.subtitle
    }
  }
}
