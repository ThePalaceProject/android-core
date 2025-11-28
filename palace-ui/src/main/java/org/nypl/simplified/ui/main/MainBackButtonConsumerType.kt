package org.nypl.simplified.ui.main

interface MainBackButtonConsumerType {

  enum class Result {
    BACK_BUTTON_CONSUMED,
    BACK_BUTTON_NOT_CONSUMED
  }

  fun onBackButtonPressed(): Result
}
