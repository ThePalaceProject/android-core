package org.librarysimplified.viewer.preview

import org.nypl.simplified.feeds.api.FeedEntryBook
import java.io.File

internal object BookPreviewModel {
  @Volatile
  internal var feedEntry: FeedEntryBook? = null

  @Volatile
  internal var audioFile: File? = null
}
