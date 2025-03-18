package org.nypl.simplified.ui.main

import io.reactivex.Observable
import org.nypl.simplified.ui.splash.SplashDependenciesType
import org.nypl.simplified.boot.api.BootEvent

class MainSplashDependencies : SplashDependenciesType {

  override val bootEvents: Observable<BootEvent> =
    MainApplication.application.servicesBootEvents
}
