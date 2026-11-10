package org.nypl.simplified.ui.catalog

import org.librarysimplified.services.api.Services
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.feeds.api.FeedAcquisitionPaths
import org.nypl.simplified.feeds.api.FeedEntryBook
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
  override fun labelsForEntry(entry: FeedEntryBook): BookCoverDebugLabels? {
    if (!this.debugLabelsEnabled()) {
      return null
    }

    val format = formatNameOf(entry)
    val drm = drmNameOf(entry)
    return BookCoverDebugLabels(
      format = format,
      drm = drm,
    )
  }

  private fun debugLabelsEnabled(): Boolean {
    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    return profiles.profileCurrent().preferences().showBookCoverDebugLabels
  }

  companion object {
    /**
     * Derive a short format name from the entry's final content types.
     */
    fun formatNameOf(entry: FeedEntryBook): String {
      val types =
        entry.acquisitions
          .flatMap { FeedAcquisitionPaths.linearize(it).flatMap { path -> path.asMIMETypes() } }
          .map { it.fullType }
          .toSet()

      fun has(vararg names: String): Boolean = names.any { it in types }

      return when {
        has(StandardFormatNames.genericEPUBFiles.fullType) -> "EPUB"
        has(StandardFormatNames.genericPDFFiles.fullType) -> "PDF"
        has(StandardFormatNames.genericJsonAudioBook.fullType) -> "Audiobook"
        else -> "unknown"
      }
    }

    /**
     * Derive a short name for the DRM system from the entry. The
     * most reliable signal is the content types of all elements of the
     * entry's acquisition paths, matched against the MIME types this codebase
     * already knows about.
     *
     * The DRM license type (ACSM, LCP license, Boundless license, ...) is
     * never the *final* content type of an acquisition path: it is the first
     * (or an intermediate) element, and the final element is the raw book
     * file (e.g., `application/epub+zip`). So we must consider the types of
     * all path elements, not just the final content types.
     */
    fun drmNameOf(entry: FeedEntryBook): String {
      val licensor = entry.licensor
      val types =
        entry.acquisitions
          .flatMap { FeedAcquisitionPaths.linearize(it).flatMap { path -> path.asMIMETypes() } }
          .map { it.fullType }
          .toSet()

      fun has(vararg names: String): Boolean = names.any { it in types }

      return when {
        has(
          StandardFormatNames.lcpLicenseFiles.fullType,
          StandardFormatNames.lcpAudioBooks.fullType,
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
}
