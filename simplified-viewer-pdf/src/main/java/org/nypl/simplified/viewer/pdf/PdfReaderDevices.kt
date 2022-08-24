package org.nypl.simplified.viewer.pdf

import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType

object PdfReaderDevices {

  /**
   * Return the device ID for the account that owns `bookID`.
   */

  fun deviceId(
    profilesController: ProfilesControllerType,
    bookID: BookID
  ): String {
    val account = profilesController.profileAccountForBook(bookID)
    val state = account.loginState
    val credentials = state.credentials

    // Yes, really return a string that says "null"
    return credentials?.adobeCredentials?.postActivationCredentials?.deviceID?.value ?: "null"
  }
}
