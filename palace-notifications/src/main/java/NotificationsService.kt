package org.nypl.simplified.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.nypl.simplified.profiles.api.ProfileNoneCurrentException
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.slf4j.LoggerFactory

class NotificationsService(
  private val context: Context,
  private val notificationResources: NotificationResourcesType,
  httpCalls: NotificationTokenHTTPCallsType,
  profilesController: ProfilesControllerType
) : NotificationsServiceType {

  private val logger =
    LoggerFactory.getLogger(NotificationsService::class.java)

  init {
    this.createNotificationChannels()

    try {
      httpCalls.registerFCMTokenForProfileAccounts(profilesController.profileCurrent())
    } catch (_: ProfileNoneCurrentException) {
      this.logger.error("No profile to register FCM token")
    }
  }

  private fun createNotificationChannels() {
    this.logger.debug("Creating notification channels.")

    val notificationManager =
      this.context.getSystemService(Context.NOTIFICATION_SERVICE)
        as? NotificationManager

    if (notificationManager == null) {
      this.logger.warn("No system notification manager is available.")
      return
    }

    // delete possible usages for the old notifications channel
    notificationManager.deleteNotificationChannel(this.notificationResources.notificationChannelNameOld)

    for (channelDescription in this.notificationResources.notificationChannels) {
      this.logger.debug("Creating notification channel: {}", channelDescription)

      try {
        val channelInfo =
          NotificationChannel(
            channelDescription.id,
            channelDescription.name,
            NotificationManager.IMPORTANCE_DEFAULT
          )

        channelInfo.description = channelDescription.description
        channelInfo.enableVibration(true)
        notificationManager.createNotificationChannel(channelInfo)
      } catch (e: Throwable) {
        this.logger.debug("Failed to create notification channel {}: ", channelDescription.id, e)
      }
    }
  }
}
