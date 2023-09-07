package org.librarysimplified.main

import io.reactivex.Observable
import org.librarysimplified.ui.splash.SplashDependenciesType
import org.nypl.simplified.boot.api.BootEvent

class MainSplashDependencies : SplashDependenciesType {

  override val bootEvents: Observable<BootEvent> =
    MainApplication.application.servicesBootEvents
}
