package org.nypl.simplified.ui.catalog

import org.librarysimplified.services.api.Services
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.feeds.api.FeedEntry.FeedEntryOPDS
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.thepalaceproject.palace.images.BookCoverDebugLabels
import org.thepalaceproject.palace.images.BookCoverDebugLabelsLookupType

/**
 * The debug-only cover labels, showing the book format and the DRM system.
 *
 * This makes it possible to find books with a specific format/DRM combination
 * in the catalog grid without opening the book. The labels are drawn only
 * while the "show book cover debug labels" preference is enabled, which is set
 * from the debug "Books" settings screen; when it is off, [labelsForEntry]
 * returns `null` and nothing is drawn.
 */

class CatalogCoverDebugLabels : BookCoverDebugLabelsLookupType {
  override fun labelsForEntry(entry: FeedEntryOPDS): BookCoverDebugLabels? {
    if (!this.debugLabelsEnabled()) {
      return null
    }

    val format = entry.probableFormat?.shortName ?: "unknown"
    val drm = this.drmNameOf(entry)
    return BookCoverDebugLabels(
      format = format,
      drm = drm
    )
  }

  private fun debugLabelsEnabled(): Boolean {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    return profiles.profileCurrent().preferences().showBookCoverDebugLabels
  }

  /**
   * Derive a short name for the DRM system from the OPDS entry. The most
   * reliable signal is the final content types of the entry's acquisitions,
   * matched against the MIME types this codebase already knows about.
   */

  private fun drmNameOf(entry: FeedEntryOPDS): String {
    val licensor = entry.feedEntry.licensor
    val types =
      entry.feedEntry.acquisitions
        .flatMap { it.availableFinalContentTypes() }
        .map { it.fullType }
        .toSet()

    fun has(vararg names: String): Boolean = names.any { it in types }

    return when {
      has(
        StandardFormatNames.lcpLicenseFiles.fullType,
        StandardFormatNames.lcpAudioBooks.fullType
      ) -> "LCP"

      has(StandardFormatNames.adobeACSMFiles.fullType) -> "Adobe"

      has(StandardFormatNames.boundlessLicenseFiles.fullType) -> "Boundless"

      has(StandardFormatNames.findawayAudioBooks.fullType) -> "Findaway"

      has(StandardFormatNames.overdriveAudioBooks.fullType) -> "Overdrive"

      // A licensor is present but we did not recognize the specific scheme.
      licensor != null -> "DRM:${licensor.vendor}"

      else -> "none"
    }
  }
}
