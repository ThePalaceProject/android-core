package org.nypl.simplified.accounts.registry.api

import com.io7m.jattribute.core.AttributeReadableType
import io.reactivex.Observable
import net.jcip.annotations.ThreadSafe
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderResolutionListenerType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.taskrecorder.api.TaskResult
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * The interface exposing a set of account providers.
 *
 * Implementations are required to be safe to access from multiple threads.
 */

@ThreadSafe
interface AccountProviderRegistryType : AutoCloseable {

  /**
   * A source of registry events.
   */

  @Deprecated("Use the status attribute instead.")
  val events: Observable<AccountProviderRegistryEvent>

  /**
   * The default, guaranteed-to-exist account provider.
   */

  val defaultProvider: AccountProviderType

  /**
   * A read-only view of the currently resolved providers.
   */

  val resolvedProviders: Map<URI, AccountProviderType>

  /**
   * The status of the account registry.
   * Updates to this attribute are guaranteed to be published on the UI thread.
   */

  val statusAttribute: AttributeReadableType<AccountProviderRegistryStatus>

  /**
   * The account provider descriptions.
   * Updates to this attribute are guaranteed to be published on the UI thread.
   */

  val accountProviderDescriptionsAttribute: AttributeReadableType<Map<URI, AccountProviderDescription>>

  /**
   * The account provider descriptions.
   * Updates to this attribute are guaranteed to be published on the UI thread.
   */

  val accountProviderDescriptionsSortedAttribute: AttributeReadableType<List<AccountProviderDescription>>

  /**
   * The status of the account registry.
   */

  val status: AccountProviderRegistryStatus
    get() = this.statusAttribute.get()

  /**
   * Ensure all account providers are loaded.
   */

  fun loadAsync(): CompletableFuture<Unit>

  /**
   * Ensure all account providers are loaded.
   */

  fun load() {
    return this.loadAsync().get()
  }

  /**
   * Refresh the available account providers.
   *
   * @param refreshRequest The refresh parameters
   */

  fun refresh(
    refreshRequest: AccountProviderRegistryRefresh
  ) {
    return this.refreshAsync(refreshRequest).get()
  }

  /**
   * Refresh the available account providers.
   *
   * @param refreshRequest The refresh parameters
   */

  fun refreshAsync(
    refreshRequest: AccountProviderRegistryRefresh
  ): CompletableFuture<Unit>

  /**
   * Clear cached account providers from all sources.
   */

  fun clear() {
    return this.clearAsync().get()
  }

  /**
   * Clear cached account providers from all sources.
   */

  fun clearAsync(): CompletableFuture<Unit>

  /**
   * Return an immutable read-only of the account provider descriptions.
   *
   * Implementations are required to implicitly call [refresh] if the method has not previously
   * been called.
   */

  fun accountProviderDescriptions(): Map<URI, AccountProviderDescription>

  /**
   * Find the account provider with the given `id`.
   *
   * Implementations are required to implicitly call [refresh] if the method has not previously
   * been called.
   */

  fun findAccountProviderDescription(id: URI): AccountProviderDescription? =
    this.accountProviderDescriptions()[id]

  /**
   * Introduce the given account provider to the registry. If an existing, newer version of the
   * given account provider already exists in the registry, the newer version is returned.
   */

  fun updateProvider(
    accountProvider: AccountProviderType
  ): AccountProviderType {
    return this.updateProviderAsync(accountProvider).get()
  }

  /**
   * Introduce the given account provider to the registry. If an existing, newer version of the
   * given account provider already exists in the registry, the newer version is returned.
   */

  fun updateProviderAsync(
    accountProvider: AccountProviderType
  ): CompletableFuture<AccountProviderType>

  /**
   * Introduce the given account provider description to the registry. If an existing, newer
   * version of the given account provider description already exists in the registry, the newer
   * version is returned.
   */

  fun updateDescription(
    description: AccountProviderDescription
  ): AccountProviderDescription {
    return this.updateDescriptionAsync(description).get()
  }

  /**
   * Introduce the given account provider description to the registry. If an existing, newer
   * version of the given account provider description already exists in the registry, the newer
   * version is returned.
   */

  fun updateDescriptionAsync(
    description: AccountProviderDescription
  ): CompletableFuture<AccountProviderDescription>

  /**
   * Introduce the given account provider description to the registry. If an existing, newer
   * version of the given account provider description already exists in the registry, the newer
   * version is returned.
   */

  fun updateDescriptions(
    descriptions: List<AccountProviderDescription>
  ): List<AccountProviderDescription> {
    return this.updateDescriptionsAsync(descriptions).get()
  }

  /**
   * Introduce the given account provider description to the registry. If an existing, newer
   * version of the given account provider description already exists in the registry, the newer
   * version is returned.
   */

  fun updateDescriptionsAsync(
    descriptions: List<AccountProviderDescription>
  ): CompletableFuture<List<AccountProviderDescription>>

  /**
   * Resolve the description into a full account provider. The given `onProgress` function
   * will be called repeatedly during the resolution process to report on the status of the
   * resolution.
   */

  fun resolveAsync(
    onProgress: AccountProviderResolutionListenerType,
    description: AccountProviderDescription
  ): CompletableFuture<TaskResult<AccountProviderType>>

  /**
   * Resolve the description into a full account provider. The given `onProgress` function
   * will be called repeatedly during the resolution process to report on the status of the
   * resolution.
   */

  fun resolve(
    onProgress: AccountProviderResolutionListenerType,
    description: AccountProviderDescription
  ): TaskResult<AccountProviderType> {
    return this.resolveAsync(onProgress, description).get()
  }
}
