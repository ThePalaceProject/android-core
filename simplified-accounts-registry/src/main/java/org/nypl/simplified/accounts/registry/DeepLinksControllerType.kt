package org.nypl.simplified.accounts.registry

import io.reactivex.Observable

interface DeepLinksControllerType {

  fun deepLinkEvents(): Observable<DeepLinkEvent>

}
