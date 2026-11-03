package org.nypl.simplified.ui.catalog

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accessibility.AccessibilityServiceType
import org.librarysimplified.ui.R

/**
 * Screen reader behavior for catalog search results: moves focus to the top
 * of the results, then announces the result count.
 */

object CatalogSearchResultsAccessibility {
  /**
   * Delay between the focus change and the result count announcement. TalkBack
   * reads the focused list first; the announcement must wait for that readout
   * to finish or it will be cut off.
   */

  private const val ANNOUNCE_DELAY_MS = 1500L

  private var pendingAnnouncement: Runnable? =
    null

  private var pendingAnchor: View? =
    null

  /**
   * Moves focus to the top of the search results and schedules the result
   * count announcement. Any pending announcement from a previous search is
   * cancelled.
   */

  fun searchResultsLoaded(
    listView: RecyclerView,
    itemCount: Int
  ) {
    this.cancelPendingAnnouncement()
    listView.post {
      listView.requestFocus()
    }
    val runnable =
      Runnable {
        val context = listView.context
        val message =
          if (itemCount == 0) {
            context.getString(R.string.feedEmpty)
          } else {
            context.getString(R.string.catalogAccessibilitySearchResultsCount, itemCount)
          }
        Services
          .serviceDirectory()
          .requireService(AccessibilityServiceType::class.java)
          .speak(message)
      }
    this.pendingAnnouncement = runnable
    this.pendingAnchor = listView
    listView.postDelayed(runnable, ANNOUNCE_DELAY_MS)
  }

  /**
   * Cancels a pending result count announcement.
   */

  fun searchCleared() {
    this.cancelPendingAnnouncement()
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
