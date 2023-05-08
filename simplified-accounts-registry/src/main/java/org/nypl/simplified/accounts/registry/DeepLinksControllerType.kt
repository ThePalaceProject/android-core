package org.nypl.simplified.accounts.registry

import io.reactivex.Observable
import org.nypl.simplified.accounts.api.AccountID

interface DeepLinksControllerType {

  fun deepLinkEvents(): Observable<DeepLinkEvent>

  fun publishDeepLinkEvent(accountID: AccountID)
}
