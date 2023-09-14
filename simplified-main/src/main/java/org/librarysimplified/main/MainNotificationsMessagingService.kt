package org.librarysimplified.main

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.slf4j.LoggerFactory
import kotlin.random.Random

class MainNotificationsMessagingService : FirebaseMessagingService() {

  companion object {
    private const val EVENT_TYPE_ACTIVITY_SYNC = "ActivitySync"
    private const val EVENT_TYPE_HOLD_AVAILABLE = "HoldAvailable"
    private const val EVENT_TYPE_LOAN_EXPIRY = "LoanExpiry"
  }

  private val logger = LoggerFactory.getLogger(MainNotificationsMessagingService::class.java)

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)

    val notificationManager =
      getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
    val data = message.data

    if (data.isEmpty()) {
      return
    }

    val eventType = data["event_type"]

    if (eventType.isNullOrBlank()) {
      return
    }

    val intent = Intent(this, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
      this, 0, intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val channelId = when (eventType) {
      EVENT_TYPE_HOLD_AVAILABLE -> {
        getString(R.string.notification_channel_id_reservations)
      }

      EVENT_TYPE_LOAN_EXPIRY -> {
        getString(R.string.notification_channel_id_loans)
      }

      EVENT_TYPE_ACTIVITY_SYNC -> {
        ""
      }

      else -> {
        this.logger.error("Unrecognized event type: {}", eventType)
        return
      }
    }

    // if the channel id is blank we don't need to display a notification
    if (channelId.isBlank()) {
      // do nothing for now
    } else {
      val title = data["title"]
      val body = data["body"]

      if (title.isNullOrBlank()) {
        this.logger.error("Invalid title for the notification")
        return
      }

      val notificationBuilder =
        NotificationCompat.Builder(applicationContext, channelId)
          .setContentTitle(title)
          .setContentText(body)
          .setSmallIcon(R.drawable.main_icon)
          .setAutoCancel(true)
          .setShowWhen(true)
          .setStyle(
            NotificationCompat.BigTextStyle()
              .setBigContentTitle(title)
              .bigText(body)
          )
          .setContentIntent(pendingIntent)
          .setWhen(System.currentTimeMillis())

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationBuilder.setChannelId(channelId)
      } else {
        notificationBuilder.setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
      }

      notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }
  }
}
