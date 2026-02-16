package org.nypl.simplified.tests.mocking

import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType

class FakeAuthorizationHandler : PlayerAuthorizationHandlerType {

  override fun onAuthorizationIsNoLongerInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    throw IllegalStateException("No response available")
  }

  override fun onAuthorizationIsInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    throw IllegalStateException("No response available")
  }

  override fun onConfigureAuthorizationFor(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ): LSHTTPAuthorizationType {
    throw IllegalStateException("No response available")
  }

  override fun <T : Any> onRequireCustomCredentialsFor(
    providerName: String,
    kind: PlayerDownloadRequest.Kind,
    credentialsType: Class<T>
  ): T {
    throw IllegalStateException("No response available")
  }
}
