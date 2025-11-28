package org.librarysimplified.viewer.audiobook

import org.slf4j.LoggerFactory

internal object AudioBookViewerModel {

  private val logger =
    LoggerFactory.getLogger(AudioBookViewerModel::class.java)

  @Volatile
  internal var parameters: AudioBookPlayerParameters? = null

  @Volatile
  internal var appliedLastReadBookmarkMigration: Boolean = false
    set(value) {
      this.logger.debug("appliedLastReadBookmarkMigration: {} -> {}", field, value)
      field = value
    }
}
