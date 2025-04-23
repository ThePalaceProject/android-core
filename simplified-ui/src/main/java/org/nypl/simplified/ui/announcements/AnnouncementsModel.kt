package org.nypl.simplified.ui.announcements

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jmulticlose.core.CloseableCollection
import org.librarysimplified.services.api.Services
import org.nypl.simplified.announcements.Announcement
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.main.MainAttributes
import org.slf4j.LoggerFactory
import java.util.SortedMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AnnouncementsModel {

  private val logger =
    LoggerFactory.getLogger(AnnouncementsModel::class.java)

  private var subscriptions =
    CloseableCollection.create()

  private val announcementsAcknowledged: MutableSet<UUID> =
    ConcurrentHashMap.newKeySet()
  private val announcementsUnacknowledged: MutableMap<UUID, EnumeratedAnnouncement> =
    ConcurrentHashMap()

  private val announcementsUnacknowledgedAttribute: AttributeType<SortedMap<UUID, EnumeratedAnnouncement>> =
    MainAttributes.attributes.withValue(sortedMapOf())
  private val announcementsUnacknowledgedAttributeUI: AttributeType<SortedMap<UUID, EnumeratedAnnouncement>> =
    MainAttributes.attributes.withValue(sortedMapOf())

  init {
    MainAttributes.wrapAttribute(
      this.announcementsUnacknowledgedAttribute,
      this.announcementsUnacknowledgedAttributeUI
    )
  }

  data class EnumeratedAnnouncement(
    val announcement: Announcement,
    val providerTitle: String,
    val index: Int,
    val count: Int
  )

  val announcements: AttributeReadableType<SortedMap<UUID, EnumeratedAnnouncement>> =
    this.announcementsUnacknowledgedAttributeUI

  fun start() {
    this.subscriptions.close()
    this.subscriptions = CloseableCollection.create()

    val services =
      Services.serviceDirectory()
    val profiles =
      services.requireService(ProfilesControllerType::class.java)

    val subscription =
      profiles.profileEvents()
        .ofType(ProfileUpdated::class.java)
        .subscribe { _ -> this.onProfileUpdated(profiles) }

    this.subscriptions.add(
      AutoCloseable { subscription.dispose() }
    )

    this.onProfileUpdated(profiles)
  }

  fun acknowledge(id: UUID) {
    try {
      val services =
        Services.serviceDirectory()
      val profiles =
        services.requireService(ProfilesControllerType::class.java)
      val profile =
        profiles.profileCurrent()
      val mostRecentAccount =
        profile.preferences().mostRecentAccount
      val account =
        profile.account(mostRecentAccount)

      val newList =
        account.preferences.announcementsAcknowledged.toSet()
          .plus(id)

      account.setPreferences(
        account.preferences.copy(announcementsAcknowledged = newList.toList())
      )
    } catch (e: Throwable) {
      this.logger.debug("Failed to update announcements: ", e)
    }
  }

  private fun onProfileUpdated(
    profiles: ProfilesControllerType
  ) {
    try {
      val profile =
        profiles.profileCurrent()
      val mostRecentAccount =
        profile.preferences().mostRecentAccount
      val account =
        profile.account(mostRecentAccount)

      this.announcementsAcknowledged.clear()
      this.announcementsAcknowledged.addAll(account.preferences.announcementsAcknowledged)

      this.announcementsUnacknowledged.clear()

      var index = 1
      var count = 0
      for (entry in account.provider.announcements) {
        if (!this.announcementsAcknowledged.contains(entry.id)) {
          ++count
        }
      }

      for (entry in account.provider.announcements) {
        if (!this.announcementsAcknowledged.contains(entry.id)) {
          this.announcementsUnacknowledged[entry.id] = EnumeratedAnnouncement(
            announcement = entry,
            providerTitle = account.provider.displayName,
            index = index,
            count = count
          )
          ++index
        }
      }

      this.announcementsUnacknowledgedAttribute.set(
        this.announcementsUnacknowledged.toSortedMap()
      )
    } catch (e: Throwable) {
      this.logger.debug("Failed to update announcements: ", e)
    }
  }
}
