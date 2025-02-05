package org.librarysimplified.ui.catalog

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
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.ui.images.ImageAccountIcons
import org.nypl.simplified.ui.images.ImageLoaderType

class CatalogToolbar(
  private val window: Window,
  private val logoTouch: ViewGroup,
  private val logo: ImageView,
  private val text: TextView,
  private val searchTouch: ViewGroup,
  private val searchIcon: ImageView,
  private val searchText: EditText,
  private val onSearchSubmitted: (FeedSearch, String) -> Unit
) {

  /**
   * Configure the toolbar for a feed.
   */

  fun configure(
    imageLoader: ImageLoaderType,
    accountProvider: AccountProviderDescription,
    feed: Feed,
    canGoBack: Boolean
  ) {
    try {
      this.text.text = feed.feedTitle
      this.searchIcon.setImageResource(R.drawable.magnifying_glass)
      this.searchText.visibility = View.INVISIBLE

      val search = feed.feedSearch
      if (search != null) {
        this.searchIcon.visibility = View.VISIBLE
        this.searchTouch.setOnClickListener {
          if (this.searchText.isVisible) {
            this.searchBoxClose()
          } else {
            this.searchBoxOpen()
          }
        }

        this.searchText.setOnEditorActionListener { v, actionId, event ->
          return@setOnEditorActionListener if (actionId == EditorInfo.IME_ACTION_DONE) {
            this.keyboardHide()
            this.onSearchSubmitted(search, this.searchText.text.trim().toString())
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
        return
      }

      /*
       * If we're at the root of a feed, then display the current account's logo in the toolbar.
       */

      ImageAccountIcons.loadAccountLogoIntoView(
        loader = imageLoader.loader,
        account = accountProvider,
        defaultIcon = R.drawable.account_default,
        iconView = this.logo
      )
    } catch (e: Throwable) {
      // Nothing to do
    }
  }

  private fun searchBoxOpen() {
    this.searchIcon.setImageResource(R.drawable.xmark)
    this.searchText.visibility = View.VISIBLE
    this.text.visibility = View.INVISIBLE

    this.text.postDelayed({ this.text.requestFocus() }, 100)
    this.text.postDelayed({ this.keyboardShow() }, 100)
  }

  private fun searchBoxClose() {
    this.searchIcon.setImageResource(R.drawable.magnifying_glass)
    this.searchText.visibility = View.INVISIBLE
    this.text.visibility = View.VISIBLE

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
