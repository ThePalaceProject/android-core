package org.nypl.simplified.ui.settings

import androidx.annotation.UiThread
import com.google.common.util.concurrent.MoreExecutors
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import org.joda.time.LocalDateTime
import org.librarysimplified.reports.Reports
import org.librarysimplified.services.api.Services
import org.nypl.drm.core.AdobeAdeptExecutorType
import org.nypl.drm.core.BoundlessServiceType
import org.nypl.simplified.adobe.extensions.AdobeDRMExtensions
import org.nypl.simplified.analytics.api.AnalyticsEvent
import org.nypl.simplified.analytics.api.AnalyticsType
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.boot.api.BootFailureTesting
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.crashlytics.api.CrashlyticsServiceType
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.profiles.api.ProfilePreferences
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.main.MainApplication
import org.nypl.simplified.ui.main.MainAttributes
import org.slf4j.LoggerFactory

object SettingsDebugModel {

  private val logger =
    LoggerFactory.getLogger(SettingsDebugModel::class.java)

  private val adeptActivationsBase: AttributeType<List<AdobeDRMExtensions.Activation>> =
    MainAttributes.attributes.withValue(listOf())
  private val adeptActivationsUI: AttributeType<List<AdobeDRMExtensions.Activation>> =
    MainAttributes.attributes.withValue(listOf())

  init {
    MainAttributes.wrapAttribute(
      this.adeptActivationsBase,
      this.adeptActivationsUI
    )
  }

  val adeptActivations: AttributeReadableType<List<AdobeDRMExtensions.Activation>> =
    this.adeptActivationsUI

  var isBootFailureEnabled: Boolean
    get() =
      BootFailureTesting.isBootFailureEnabled(MainApplication.application)
    set(value) {
      BootFailureTesting.enableBootFailures(
        context = MainApplication.application,
        enabled = value
      )
    }

  @UiThread
  fun sendErrorLogs() {
    val services =
      Services.serviceDirectory()
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    Reports.sendReportsDefault(
      context = MainApplication.application,
      address = buildConfig.supportErrorReportEmailAddress,
      body = ""
    )
  }

  @UiThread
  fun sendAnalytics() {
    val services =
      Services.serviceDirectory()
    val analytics =
      services.requireService(AnalyticsType::class.java)

    analytics.publishEvent(
      AnalyticsEvent.SyncRequested(
        timestamp = LocalDateTime.now(),
        credentials = null
      )
    )
  }

  @UiThread
  fun syncAccounts() {
    val services =
      Services.serviceDirectory()
    val profilesController =
      services.requireService(ProfilesControllerType::class.java)
    val booksController =
      services.requireService(BooksControllerType::class.java)

    try {
      profilesController.profileCurrent()
        .accounts()
        .keys
        .forEach { account -> booksController.booksSync(account) }
    } catch (e: Exception) {
      this.logger.debug("ouch: ", e)
    }
  }

  @UiThread
  fun forgetAllAnnouncements() {
    val services =
      Services.serviceDirectory()
    val profilesController =
      services.requireService(ProfilesControllerType::class.java)

    try {
      val profile = profilesController.profileCurrent()
      val accounts = profile.accounts()
      for ((_, account) in accounts) {
        account.setPreferences(account.preferences.copy(announcementsAcknowledged = listOf()))
      }
    } catch (e: Exception) {
      this.logger.debug("could not forget announcements: ", e)
    }
  }

  @UiThread
  fun updatePreferences(
    update: (ProfilePreferences) -> ProfilePreferences
  ) {
    val services =
      Services.serviceDirectory()
    val profilesController =
      services.requireService(ProfilesControllerType::class.java)

    try {
      profilesController.profileUpdate { description ->
        description.copy(preferences = update(description.preferences))
      }
    } catch (e: Exception) {
      this.logger.debug("Could not update preferences: ", e)
    }
  }

  @UiThread
  fun showTestingLibraries(): Boolean {
    return this.preferences().showTestingLibraries
  }

  private fun preferences(): ProfilePreferences {
    val services =
      Services.serviceDirectory()
    val profilesController =
      services.requireService(ProfilesControllerType::class.java)

    return profilesController.profileCurrent().preferences()
  }

  @UiThread
  fun setShowOnlySupportedBooks(
    showOnlySupported: Boolean
  ) {
    val services =
      Services.serviceDirectory()
    val feedLoader =
      services.requireService(FeedLoaderType::class.java)

    feedLoader.showOnlySupportedBooks = showOnlySupported
  }

  @UiThread
  fun appVersion(): String {
    val services =
      Services.serviceDirectory()
    val buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)

    return buildConfig.simplifiedVersion
  }

  @UiThread
  fun hasSeenLibrarySelection(): Boolean {
    return this.preferences().hasSeenLibrarySelectionScreen
  }

  @UiThread
  fun isManualLCPPassphraseEnabled(): Boolean {
    return this.preferences().isManualLCPPassphraseEnabled
  }

  @UiThread
  fun showOnlySupportedBooks(): Boolean {
    val services =
      Services.serviceDirectory()
    val feedLoader =
      services.requireService(FeedLoaderType::class.java)

    return feedLoader.showOnlySupportedBooks
  }

  @UiThread
  fun crashlyticsId(): String {
    val services =
      Services.serviceDirectory()
    val crashlytics =
      services.requireService(CrashlyticsServiceType::class.java)

    val userId = crashlytics.userId ?: return "Crashlytics is not enabled."
    if (userId == "") {
      return "(Unassigned)"
    }
    return userId
  }

  @UiThread
  fun adeptSupported(): Boolean {
    val services = Services.serviceDirectory()
    return services.optionalService(AdobeAdeptExecutorType::class.java) != null
  }

  @UiThread
  fun boundlessSupported(): Boolean {
    val services = Services.serviceDirectory()
    return services.optionalService(BoundlessServiceType::class.java) != null
  }

  @UiThread
  fun fetchAdobeActivations() {
    val services =
      Services.serviceDirectory()
    val adeptExecutor =
      services.optionalService(AdobeAdeptExecutorType::class.java)

    if (adeptExecutor == null) {
      this.adeptActivationsBase.set(listOf())
      return
    }

    val adeptFuture =
      AdobeDRMExtensions.getDeviceActivations(
        adeptExecutor,
        { message -> this.logger.error("DRM: {}", message) },
        { message -> this.logger.debug("DRM: {}", message) }
      )

    adeptFuture.addListener(
      {
        val activations = try {
          adeptFuture.get()
        } catch (e: Exception) {
          this.logger.debug("could not retrieve activations: ", e)
          emptyList()
        }
        this.adeptActivationsBase.set(activations)
      },
      MoreExecutors.directExecutor()
    )
  }
}
