package org.librarysimplified.main

import android.content.Context
import org.nypl.simplified.notifications.NotificationChannelInfo
import org.nypl.simplified.notifications.NotificationResourcesType

/**
 * Resources used by the notifications service.
 */

class MainNotificationResources(
  private val context: Context
) : NotificationResourcesType {

  override val notificationChannels: List<NotificationChannelInfo>
    get() = listOf(
      NotificationChannelInfo(
        id = context.getString(R.string.notification_channel_id_reservations),
        name = context.getString(R.string.notification_channel_name_reservations),
        description = context.getString(R.string.notification_channel_description_reservations)
      ),
      NotificationChannelInfo(
        id = context.getString(R.string.notification_channel_id_loans),
        name = context.getString(R.string.notification_channel_name_loans),
        description = context.getString(R.string.notification_channel_description_loans)
      )
    )

  override val notificationChannelNameOld: String
    get() = context.getString(R.string.notification_channel_name_old)
}
