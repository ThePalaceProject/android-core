package org.thepalaceproject.ui.auto

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import org.slf4j.LoggerFactory

class PalaceCarAppService : CarAppService() {

  private val logger =
    LoggerFactory.getLogger(PalaceCarAppService::class.java)

  override fun createHostValidator(): HostValidator {
    this.logger.debug("createHostValidator")
    return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
  }

  override fun onCreateSession(): Session {
    this.logger.debug("onCreateSession")
    return super.onCreateSession()
  }

  override fun onCreateSession(sessionInfo: SessionInfo): Session {
    this.logger.debug("onCreateSession: {}", sessionInfo)
    return super.onCreateSession(sessionInfo)
  }

  override fun onCreate() {
    this.logger.debug("onCreate")
    super.onCreate()
  }

  override fun onDestroy() {
    this.logger.debug("onDestroy")
    super.onDestroy()
  }
}
