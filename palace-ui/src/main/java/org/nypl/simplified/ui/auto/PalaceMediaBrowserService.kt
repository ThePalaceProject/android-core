package org.nypl.simplified.ui.auto

import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import org.librarysimplified.audiobook.views.mediacontrols.PlayerMediaFacade
import org.slf4j.LoggerFactory

class PalaceMediaBrowserService : MediaLibraryService() {

  private val logger =
    LoggerFactory.getLogger(PalaceMediaBrowserService::class.java)

  private lateinit var session: MediaLibrarySession

  override fun onCreate() {
    this.logger.debug("onCreate")

    try {
      super.onCreate()
      this.session =
        MediaLibrarySession.Builder(this, PlayerMediaFacade, PalaceMediaLibraryCallback())
          .build()
    } catch (e: Throwable) {
      this.logger.debug("onCreate: ", e)
    }
  }

  override fun onGetSession(
    controllerInfo: MediaSession.ControllerInfo
  ): MediaLibrarySession {
    this.logger.debug("onGetSession")
    return this.session
  }

  override fun onDestroy() {
    this.logger.debug("onDestroy")

    try {
      this.session.release()
      super.onDestroy()
    } catch (e: Throwable) {
      this.logger.debug("onDestroy: ", e)
    }
  }
}
