package org.nypl.simplified.notifications

interface NotificationResourcesType {
  val notificationChannels: List<NotificationChannelInfo>

  val notificationChannelNameOld: String
}
