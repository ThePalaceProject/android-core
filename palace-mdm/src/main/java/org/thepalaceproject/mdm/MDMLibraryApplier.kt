package org.thepalaceproject.mdm

import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.MoreExecutors
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.slf4j.LoggerFactory
import org.thepalaceproject.palace.diagnostics.api.DiagnosticCodes
import org.thepalaceproject.palace.diagnostics.api.DiagnosticsServiceType
import java.net.URI

/**
 * A class that applies the managed device (MDM) configuration to the current
 * profile.
 *
 * The configured libraries are added to the patron's library list, and the
 * first configured library that has an account is selected as the current
 * library. Application is idempotent: libraries that are already present in
 * the profile are left alone, and libraries that the patron added are never
 * removed.
 *
 * All failures are reported to the diagnostics backend and never
 * propagate.
 */
class MDMLibraryApplier(
  private val profiles: ProfilesControllerType,
  private val diagnostics: DiagnosticsServiceType,
) {
  private val logger =
    LoggerFactory.getLogger(MDMLibraryApplier::class.java)

  /**
   * Apply the given parsed managed device configuration to the current
   * profile.
   *
   * The application is a no-op if the `enableMDMLibraries` profile
   * preference is not set.
   *
   * @param result The parsed managed device configuration
   */
  fun apply(result: MDMConfigurationParseResult) {
    if (!this.profiles
        .profileCurrent()
        .preferences()
        .enableMDMLibraries
    ) {
      this.logger.debug("MDM library application is disabled; not applying managed configuration")
      return
    }

    for (field in result.invalidFields) {
      this.diagnostics.report(
        code = DiagnosticCodes.MDM_CONFIGURATION_INVALID_FIELD,
        data =
          mapOf(
            Pair("Field", field.field),
            Pair("Value", field.value),
          ),
        throwable = null,
      )
    }

    val configuredURIs =
      result.configuration.libraryURIs
    if (configuredURIs.isEmpty()) {
      return
    }

    val profile =
      this.profiles.profileCurrent()
    for (uri in configuredURIs) {
      if (profile.accountsByProvider()[uri] != null) {
        continue
      }
      val future =
        this.profiles.profileAccountCreate(uri)
      future.addListener(
        {
          this.onAccountCreateComplete(uri, future, configuredURIs)
        },
        MoreExecutors.directExecutor(),
      )
    }

    this.selectLibrary(configuredURIs)
  }

  private fun onAccountCreateComplete(
    uri: URI,
    future: FluentFuture<TaskResult<AccountType>>,
    configuredURIs: List<URI>,
  ) {
    try {
      val result =
        future.get()
      if (result is TaskResult.Failure) {
        this.logger.debug("Failed to create MDM library account for {}: {}", uri, result.message)
        this.diagnostics.report(
          code = DiagnosticCodes.MDM_LIBRARY_CREATE_FAILED,
          data =
            mapOf(
              Pair("Provider", uri.toString()),
              Pair("ExceptionType", result.exception?.javaClass?.name ?: "Unknown"),
            ),
          throwable = result.exception,
        )
      }
    } catch (e: Throwable) {
      this.logger.debug("Unexpected error waiting for MDM library account creation: ", e)
    }

    this.selectLibrary(configuredURIs)
  }

  private fun selectLibrary(configuredURIs: List<URI>) {
    val profile =
      this.profiles.profileCurrent()
    for (uri in configuredURIs) {
      val account =
        profile.accountsByProvider()[uri]
      if (account == null) {
        continue
      }
      if (profile.preferences().mostRecentAccount == account.id) {
        return
      }
      this.profiles.profileUpdate { description ->
        description.copy(
          preferences =
            description.preferences.copy(mostRecentAccount = account.id),
        )
      }
      return
    }
  }
}
