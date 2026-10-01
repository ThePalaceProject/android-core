package org.thepalaceproject.palace.images

import org.nypl.simplified.feeds.api.FeedEntry

/**
 * A function to look up debug-only text labels (format + DRM) for a cover.
 */

interface BookCoverDebugLabelsLookupType {
  /**
   * @return The debug labels to render for the given book, or `null` to render nothing.
   *         Implementations are expected to return `null` in release builds.
   */

  fun labelsForEntry(entry: FeedEntry.FeedEntryOPDS): BookCoverDebugLabels?
}
