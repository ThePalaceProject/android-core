package org.librarysimplified.viewer.audiobook

import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerNoOp
import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.slf4j.LoggerFactory

internal object AudioBookViewerModel {

  private val logger =
    LoggerFactory.getLogger(AudioBookViewerModel::class.java)

  @Volatile
  internal var parameters: AudioBookPlayerParameters? = null

  @Volatile
  internal var authorizationHandler: PlayerAuthorizationHandlerType =
    PlayerAuthorizationHandlerNoOp

  @Volatile
  internal var loginHandler: () -> Unit = {
    // Nothing!
  }

  @Volatile
  internal var appliedLastReadBookmarkMigration: Boolean = false
    set(value) {
      this.logger.debug("appliedLastReadBookmarkMigration: {} -> {}", field, value)
      field = value
    }
}
