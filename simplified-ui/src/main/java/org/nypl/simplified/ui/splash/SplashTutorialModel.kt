package org.nypl.simplified.ui.splash

object SplashTutorialModel {

  private var pageIndexField = 0

  fun pageNext(): Int? {
    if (this.pageIndexField >= 2) {
      return null
    }
    this.pageIndexField += 1
    return this.pageIndexField
  }

  fun pagePrevious(): Int {
    if (this.pageIndexField <= 0) {
      return 0
    }
    this.pageIndexField -= 1
    return this.pageIndexField
  }

  fun pageReset() {
    this.pageIndexField = 0
  }
}
