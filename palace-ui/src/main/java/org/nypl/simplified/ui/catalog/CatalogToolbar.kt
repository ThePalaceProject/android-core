package org.nypl.simplified.ui.catalog

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.ui.catalog.CatalogPart.BOOKS
import org.nypl.simplified.ui.catalog.CatalogPart.CATALOG
import org.nypl.simplified.ui.catalog.CatalogPart.HOLDS
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType
import java.net.URI

class CatalogToolbar(
  private val logo: ImageView,
  private val logoTouch: ViewGroup,
  private val onToolbarBackPressed: () -> Unit,
  private val onToolbarLogoPressed: () -> Unit,
  private val onSearchSubmitted: (AccountID, FeedSearch, String) -> Unit,
  private val searchIcon: ImageView,
  private val searchText: EditText,
  private val searchTouch: ViewGroup,
  private val textContainer: ViewGroup,
  private val textIconView: ImageView,
  private val text: TextView,
  private val window: Window,
) {

  /**
   * Configure the toolbar for a feed.
   */

  fun configure(
    resources: Resources,
    imageLoader: ImageLoaderType,
    account: AccountType,
    title: String,
    search: FeedSearch?,
    canGoBack: Boolean,
    catalogPart: CatalogPart,
    icon: URI?
  ) {
    try {
      when (catalogPart) {
        CATALOG -> {
          this.textContainer.visibility = View.VISIBLE
          ImageAccountIcons.loadAccountLogoIntoView(
            loader = imageLoader.loader,
            account = account.provider.toDescription(),
            defaultIcon = R.drawable.account_default,
            iconView = this.textIconView
          )
        }
        BOOKS -> {
          this.textContainer.visibility = View.INVISIBLE
        }
        HOLDS -> {
          this.textContainer.visibility = View.INVISIBLE
        }
      }

      this.text.text = title
      this.searchIcon.setImageResource(R.drawable.magnifying_glass)
      this.searchText.visibility = View.INVISIBLE

      if (search != null) {
        this.searchIcon.visibility = View.VISIBLE
        this.searchTouch.contentDescription =
          resources.getString(R.string.catalogAccessibilitySearch)
        this.searchTouch.setOnClickListener {
          if (this.searchText.isVisible) {
            this.searchBoxClose()
          } else {
            this.searchBoxOpen()
          }
        }

        this.searchText.setOnEditorActionListener { v, actionId, event ->
          return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_DONE) {
            val queryText = this.searchText.text.trim().toString()
            this.keyboardHide()
            if (queryText.isNotBlank()) {
              this.onSearchSubmitted(account.id, search, queryText)
            }
            true
          } else {
            false
          }
        }
      } else {
        this.searchTouch.visibility = View.GONE
      }

      /*
       * If we're not at the root of a feed, then display a back arrow in the toolbar.
       */

      if (canGoBack) {
        this.logo.setImageResource(org.thepalaceproject.theme.core.R.drawable.palace_arrow_back_24)
        this.logoTouch.setOnClickListener { this.onToolbarBackPressed.invoke() }
        this.logoTouch.contentDescription = resources.getString(R.string.catalogAccessibilityGoBack)
        return
      }

      /*
       * If we're at the root of a feed in the Catalog part, then display the current account's
       * logo in the toolbar. Clicking it will open an account selection dialog. If we're in the
       * Books or Reservations part, however, the icon does nothing.
       */

      when (catalogPart) {
        CATALOG -> {
          this.logoTouch.isEnabled = true
          this.logoTouch.setOnClickListener { this.onToolbarLogoPressed.invoke() }
          this.logo.setImageResource(R.drawable.main_icon)
          this.logoTouch.contentDescription =
            resources.getString(R.string.catalogAccessibilityAccountSelection)
        }

        BOOKS, HOLDS -> {
          this.logoTouch.isEnabled = false
        }
      }
    } catch (e: Throwable) {
      // Nothing to do
    }
  }

  fun requestFocus() {
    if (this.logoTouch.isEnabled) {
      this.logoTouch.requestFocus()
    } else {
      this.searchTouch.requestFocus()
    }
  }

  private fun searchBoxOpen() {
    this.searchIcon.setImageResource(R.drawable.xmark)
    this.searchText.visibility = View.VISIBLE
    this.textContainer.visibility = View.INVISIBLE

    this.text.postDelayed({ this.searchText.requestFocus() }, 100)
    this.text.postDelayed({ this.keyboardShow() }, 100)
  }

  private fun searchBoxClose() {
    this.searchIcon.setImageResource(R.drawable.magnifying_glass)
    this.searchText.visibility = View.INVISIBLE
    this.textContainer.visibility = View.VISIBLE

    this.searchText.postDelayed({ this.keyboardHide() }, 100)
  }

  private fun keyboardHide() {
    try {
      WindowInsetsControllerCompat(this.window, this.searchText)
        .hide(WindowInsetsCompat.Type.ime())
    } catch (e: Throwable) {
      // No sensible response.
    }
  }

  private fun keyboardShow() {
    try {
      WindowInsetsControllerCompat(this.window, this.searchText)
        .show(WindowInsetsCompat.Type.ime())
    } catch (e: Throwable) {
      // No sensible response.
    }
  }
}
