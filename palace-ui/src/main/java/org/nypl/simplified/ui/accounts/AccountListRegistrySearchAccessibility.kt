package org.nypl.simplified.ui.accounts

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accessibility.AccessibilityServiceType
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.librarysimplified.ui.R

/**
 * Screen reader behavior for the account list registry search box: announces
 * the result count after the user stops typing, and provides the hint for
 * the search field.
 */

object AccountListRegistrySearchAccessibility {
  /**
   * Delay before the results announcement. A screen reader must not speak on
   * every keystroke.
   */

  private const val ANNOUNCE_DELAY_MS = 750L

  private var lastResults: List<AccountProviderDescription> =
    listOf()

  private var pendingAnnouncement: Runnable? =
    null

  private var pendingAnchor: View? =
    null

  /**
   * Records the result set currently shown in the list.
   */

  fun resultsPublished(items: List<AccountProviderDescription>) {
    this.lastResults = items
  }

  /**
   * Schedules the results announcement after a delay. Any pending announcement
   * is cancelled; the count is read when the announcement fires.
   */

  fun searchChanged(anchor: View) {
    this.cancelPendingAnnouncement()
    val runnable =
      Runnable {
        val context = anchor.context
        val count = this.lastResults.size
        val message =
          if (count == 0) {
            context.getString(R.string.settingsAccessibilityNoLibrariesFound)
          } else {
            context.getString(R.string.settingsAccessibilityLibrariesFound, count)
          }
        Services
          .serviceDirectory()
          .requireService(AccessibilityServiceType::class.java)
          .speak(message)
      }
    this.pendingAnnouncement = runnable
    this.pendingAnchor = anchor
    anchor.postDelayed(runnable, ANNOUNCE_DELAY_MS)
  }

  /**
   * Cancels a pending results announcement.
   */

  fun searchCleared() {
    this.cancelPendingAnnouncement()
  }

  /**
   * Screen readers read the hint of an empty editable field. When a screen
   * reader is active, the hint conveys that results update as the user types.
   */

  fun searchHint(context: Context): CharSequence {
    val accessibilityManager =
      context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return if (accessibilityManager.isTouchExplorationEnabled()) {
      context.getString(R.string.settingsAccessibilitySearchHint)
    } else {
      context.getString(R.string.catalogSearch)
    }
  }

  private fun cancelPendingAnnouncement() {
    val pending = this.pendingAnnouncement
    val anchor = this.pendingAnchor
    if (pending != null && anchor != null) {
      anchor.removeCallbacks(pending)
    }
    this.pendingAnnouncement = null
    this.pendingAnchor = null
  }
}
