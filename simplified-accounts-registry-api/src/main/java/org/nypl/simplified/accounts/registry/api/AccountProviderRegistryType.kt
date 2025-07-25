package org.nypl.simplified.accounts.registry.api

import com.io7m.jattribute.core.AttributeReadableType
import io.reactivex.Observable
import net.jcip.annotations.ThreadSafe
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.accounts.api.AccountProviderResolutionListenerType
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.api.AccountSearchQuery
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
   */

  val statusAttribute: AttributeReadableType<AccountProviderRegistryStatus>

  /**
   * The status of the account registry.
   */

  val accountProviderDescriptionsAttribute: AttributeReadableType<Map<URI, AccountProviderDescription>>

  /**
   * The status of the account registry.
   */

  val status: AccountProviderRegistryStatus
    get() = this.statusAttribute.get()

  /**
   * Refresh the available account providers from all sources.
   *
   * @param includeTestingLibraries A hint for providers indicating whether
   * testing libraries should be loaded. May be ignored by some providers.
   * @param useCache A hint for providers indicating that they should use any cache they have
   * in order to speed up loading.
   */

  fun refresh(
    includeTestingLibraries: Boolean,
    useCache: Boolean = true
  )

  /**
   * Refresh the available account providers from all sources.
   *
   * @param includeTestingLibraries A hint for providers indicating whether
   * testing libraries should be loaded. May be ignored by some providers.
   * @param useCache A hint for providers indicating that they should use any cache they have
   * in order to speed up loading.
   */

  fun refreshAsync(
    includeTestingLibraries: Boolean,
    useCache: Boolean = true
  ): CompletableFuture<Unit>

  /**
   * Execute a search query on the registry.
   *
   * @param query The search query parameters
   */

  fun query(query: AccountSearchQuery)

  /**
   * Clear cached account providers from all sources.
   */

  fun clear()

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

  fun updateProvider(accountProvider: AccountProviderType): AccountProviderType

  /**
   * Introduce the given account provider description to the registry. If an existing, newer
   * version of the given account provider description already exists in the registry, the newer
   * version is returned.
   */

  fun updateDescription(description: AccountProviderDescription): AccountProviderDescription

  /**
   * Resolve the description into a full account provider. The given `onProgress` function
   * will be called repeatedly during the resolution process to report on the status of the
   * resolution.
   */

  fun resolve(
    onProgress: AccountProviderResolutionListenerType,
    description: AccountProviderDescription
  ): TaskResult<AccountProviderType>
}
