package org.nypl.simplified.ui.settings

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.documents.DocumentStoreType
import org.librarysimplified.services.api.ServiceDirectoryType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType

object SettingsModel {

  private val services: ServiceDirectoryType =
    Services.serviceDirectory()
  private val uiThread =
    this.services.requireService(UIThreadServiceType::class.java)

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

  init {
    this.profilesController.accountEvents()
      .subscribe { e ->
        this.uiThread.runOnUIThread {
          this.accountEventSource.onNext(e)
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
