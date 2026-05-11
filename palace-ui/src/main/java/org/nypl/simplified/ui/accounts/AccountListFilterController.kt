package org.nypl.simplified.ui.accounts

import org.nypl.simplified.accounts.api.AccountProviderDescription
import java.util.concurrent.atomic.AtomicReference

class AccountListFilterController(
  val onPublish: (List<AccountProviderDescription>) -> Unit
) {
  private var fullList: List<AccountProviderDescription> =
    listOf()

  private val filter: AtomicReference<(AccountProviderDescription) -> Boolean> =
    AtomicReference()

  fun submit(
    list: List<AccountProviderDescription>
  ) {
    this.fullList = list.toList()
    this.publish()
  }

  fun filterUnset() {
    this.filter.set(null)
    this.publish()
  }

  fun filterSet(
    predicate: ((AccountProviderDescription) -> Boolean)
  ) {
    this.filter.set(predicate)
    this.publish()
  }

  private fun publish() {
    val filterNow = this.filter.get()
    val filtered =
      if (filterNow != null) {
        this.fullList.filter(filterNow)
      } else {
        this.fullList
      }

    this.onPublish(filtered)
  }
}
