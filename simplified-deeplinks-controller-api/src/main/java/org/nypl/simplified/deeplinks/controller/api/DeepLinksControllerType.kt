package org.nypl.simplified.deeplinks.controller.api

import io.reactivex.Observable
import org.nypl.simplified.accounts.api.AccountID

interface DeepLinksControllerType {

  fun deepLinkEvents(): Observable<DeepLinkEvent>

  fun publishDeepLinkEvent(accountID: AccountID, screenID: ScreenID, barcode: String?)
}
