package org.nypl.simplified.tests.mocking

import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.downloads.LSHTTPDownloadState
import org.nypl.drm.core.BoundlessCMTemplatedLink
import org.nypl.drm.core.BoundlessFulfilledCMEPUB
import org.nypl.drm.core.BoundlessServiceType
import org.nypl.drm.core.ContentProtectionCloseableType
import org.nypl.drm.core.DRMTaskResult
import java.io.File
import java.security.KeyPair
import java.time.OffsetDateTime
import java.util.UUID

class MockBoundless : BoundlessServiceType {

  var deviceIDProperty: UUID =
    UUID.randomUUID()

  override fun createContentProtection(
    epubFile: File,
    licenseFile: File,
    tempDirectory: File,
    inMemorySizeThreshold: ULong,
    currentTime: OffsetDateTime
  ): ContentProtectionCloseableType {
    TODO("Not yet implemented")
  }

  override fun deviceID(): UUID {
    return this.deviceIDProperty
  }

  var fulfillProperty: DRMTaskResult<BoundlessFulfilledCMEPUB> =
    DRMTaskResult.DRMTaskCancelled<BoundlessFulfilledCMEPUB>(mapOf(), listOf())

  override fun fulfillEPUB(
    httpClient: LSHTTPClientType,
    link: BoundlessCMTemplatedLink,
    credentials: LSHTTPAuthorizationType?,
    outputFile: File,
    outputLicenseFile: File,
    isCancelled: () -> Boolean,
    onDownloadEvent: (LSHTTPDownloadState) -> Unit
  ): DRMTaskResult<BoundlessFulfilledCMEPUB> {
    return this.fulfillProperty
  }

  override fun keyPair(): KeyPair {
    TODO("Not yet implemented")
  }
}
