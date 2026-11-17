package org.librarysimplified.viewer.preview

import org.nypl.simplified.feeds.api.FeedEntryBook
import java.io.File

/**
 * The data passed to a book preview screen via [BookPreviewModel].
 *
 * [audioFile] is null for previews that have no audio (e.g. the generic
 * preview activity) and non-null for audiobook previews.
 */
internal data class BookPreviewModelData(
  val feedEntry: FeedEntryBook,
  val audioFile: File?,
)

internal object BookPreviewModel {
  @Volatile
  internal var current: BookPreviewModelData? = null
}
