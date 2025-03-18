package org.nypl.simplified.ui.settings

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.documents.DocumentStoreType
import org.librarysimplified.services.api.ServiceDirectoryType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.images.ImageLoaderType

object SettingsModel {

  private val services: ServiceDirectoryType =
    Services.serviceDirectory()

  val buildConfig =
    this.services.requireService(BuildConfigurationServiceType::class.java)

  val documents =
    this.services.requireService(DocumentStoreType::class.java)

  val imageLoader: ImageLoaderType =
    this.services.requireService(ImageLoaderType::class.java)

  val profilesController =
    this.services.requireService(ProfilesControllerType::class.java)

  private val accountEventSource: Subject<AccountEvent> =
    PublishSubject.create<AccountEvent>()
      .toSerialized()

  val accountEvents: Observable<AccountEvent> =
    this.accountEventSource

  private val profileEventSource: Subject<ProfileEvent> =
    PublishSubject.create<ProfileEvent>()
      .toSerialized()

  val profileEvents: Observable<ProfileEvent> =
    this.profileEventSource

  private var debugClicks = 0

  fun onClickVersion() {
    ++this.debugClicks
    if (this.debugClicks >= 7) {
      this.debugClicks = 0
      this.profilesController.profileUpdate { d ->
        d.copy(preferences = d.preferences.copy(
          showDebugSettings = !d.preferences.showDebugSettings)
        )
      }
    }
  }

  fun setMostRecentAccount(
    id: AccountID
  ) {
    this.profilesController.profileUpdate { d ->
      d.copy(preferences = d.preferences.copy(mostRecentAccount = id))
    }
  }

  fun isAccountSelected(
    item: AccountType
  ): Boolean {
    return this.profilesController.profileCurrent()
      .preferences()
      .mostRecentAccount == item.id
  }

  init {
    this.profilesController.accountEvents()
      .subscribe { e ->
        UIThread.runOnUIThread {
          this.accountEventSource.onNext(e)
        }
      }
    this.profilesController.profileEvents()
      .subscribe { e ->
        UIThread.runOnUIThread {
          this.profileEventSource.onNext(e)
        }
      }
  }

  var showDebugSettings: Boolean
    get() = this.profilesController
      .profileCurrent()
      .preferences()
      .showDebugSettings
    set(value) {
      this.profilesController.profileUpdate { description ->
        description.copy(
          preferences = description.preferences.copy(
            showDebugSettings = value
          )
        )
      }
    }
}
