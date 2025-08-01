package org.nypl.simplified.viewer.api

import android.app.Activity
import org.joda.time.LocalDateTime
import org.librarysimplified.mdc.MDCKeys
import org.librarysimplified.services.api.Services
import org.nypl.simplified.analytics.api.AnalyticsEvent
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.opds.core.getOrNull
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.viewer.spi.ViewerPreferences
import org.nypl.simplified.viewer.spi.ViewerProviderType
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.util.ServiceLoader

/**
 * A simple API to find and use a viewer provider for a given book.
 *
 * @see [ViewerProviderType]
 */

object Viewers {

  private val logger = LoggerFactory.getLogger(Viewers::class.java)

  private val services by lazy {
    Services.serviceDirectory()
  }
  private val analyticsService by lazy {
    this.services.optionalService(AnalyticsType::class.java)
  }
  private val profilesController by lazy {
    this.services.requireService(ProfilesControllerType::class.java)
  }

  /**
   * Attempt to open a viewer for a given book.
   */

  fun openViewer(
    context: Activity,
    preferences: ViewerPreferences,
    book: Book,
    format: BookFormat
  ) {
    MDC.put(MDCKeys.BOOK_DRM, format.drmInformation.kind.name)
    MDC.put(MDCKeys.BOOK_FORMAT, format.contentType.toString())
    MDC.put(MDCKeys.BOOK_INTERNAL_ID, book.id.value())
    MDC.put(MDCKeys.BOOK_ID, book.entry.id)
    MDC.put(MDCKeys.BOOK_TITLE, book.entry.title)
    MDCKeys.put(MDCKeys.BOOK_PUBLISHER, book.entry.publisher)

    this.logger.debug("Open: {} ({})", book.id, book.entry.title)
    val providers =
      ServiceLoader.load(ViewerProviderType::class.java)
        .toList()

    this.logger.debug("{} viewer providers available", providers.size)

    for (index in providers.indices) {
      val viewerProvider = providers[index]
      this.logger.debug("[{}]: {}", index, viewerProvider.name)
    }

    this.logger.debug("Trying all providers...")
    for (index in providers.indices) {
      val viewerProvider = providers[index]
      val supported = viewerProvider.canSupport(preferences, book, format)
      if (supported) {
        this.logger.debug(
          "[{}] Viewer provider {} supports the book, using it!", index, viewerProvider.name
        )

        val profile = this.profilesController.profileCurrent()
        val account = profile.account(book.account)

        // Publish 'BookOpened' event.
        this.analyticsService?.publishEvent(
          AnalyticsEvent.BookOpened(
            timestamp = LocalDateTime.now(),
            credentials = account.loginState.credentials,
            profileUUID = profile.id.uuid,
            profileDisplayName = profile.displayName,
            accountProvider = account.provider.id,
            accountUUID = account.id.uuid,
            opdsEntry = book.entry,
            targetURI = book.entry.analytics.getOrNull(),
            onAccessTokenUpdated = { accessToken ->
              account.updateBasicTokenCredentials(accessToken)
            }
          )
        )

        viewerProvider.open(context, preferences, book, format, account.provider.id)
        return
      } else {
        this.logger.debug(
          "[{}] Viewer provider {} does not support the book", index, viewerProvider.name
        )
      }
    }
    this.logger.error("No viewer providers can handle the given book")
  }
}
