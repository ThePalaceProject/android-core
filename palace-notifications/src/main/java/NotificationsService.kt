package org.nypl.simplified.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
    createNotificationChannels()

    try {
      httpCalls.registerFCMTokenForProfileAccounts(profilesController.profileCurrent())
    } catch (exception: ProfileNoneCurrentException) {
      logger.error("No profile to register FCM token")
    }
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as?
      NotificationManager ?: return

    // delete possible usages for the old notifications channel
    notificationManager.deleteNotificationChannel(notificationResources.notificationChannelNameOld)

    // there's no harm on constantly creating notification channels
    notificationResources.notificationChannels.forEach { channel ->
      this.logger.debug("Creating notification channel: {}", channel)

      notificationManager.createNotificationChannel(
        NotificationChannel(
          channel.id,
          channel.name,
          NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
          description = channel.description
          enableVibration(true)
        }
      )
    }
  }
}
