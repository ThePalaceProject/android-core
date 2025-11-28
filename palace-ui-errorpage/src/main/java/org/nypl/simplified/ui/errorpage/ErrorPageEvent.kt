package org.nypl.simplified.ui.errorpage

sealed class ErrorPageEvent {
  /**
   * The patron is tired of looking at the error page.
   */

  object GoUpwards : ErrorPageEvent()
}
