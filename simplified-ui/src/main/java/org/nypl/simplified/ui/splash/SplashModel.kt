package org.nypl.simplified.ui.splash

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeSubscriptionType
import com.io7m.jattribute.core.AttributeType
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.ui.main.MainAttributes
import java.util.concurrent.ExecutorService

object SplashModel {

  private val accountProvidersActual: AttributeType<List<AccountProviderDescription>> =
    MainAttributes.attributes.withValue(listOf())
  private val accountProvidersActualUI: AttributeType<List<AccountProviderDescription>> =
    MainAttributes.attributes.withValue(listOf())

  init {
    MainAttributes.wrapAttribute(this.accountProvidersActual, this.accountProvidersActualUI)
  }

  val accountProviders: AttributeReadableType<List<AccountProviderDescription>>
    get() = this.accountProvidersActualUI

  fun accountProvidersLoad(
    executor: ExecutorService,
    registry: AccountProviderRegistryType
  ): AttributeSubscriptionType {
    executor.execute { registry.refresh(false) }
    return registry.statusAttribute.subscribe { _, status ->
      when (status) {
        AccountProviderRegistryStatus.Idle -> {
          this.accountProvidersActual.set(
            registry.accountProviderDescriptions().values
              .sortedBy { d -> d.title }
          )
        }

        AccountProviderRegistryStatus.Refreshing -> {
          this.accountProvidersActual.set(listOf())
        }
      }
    }
  }
}
