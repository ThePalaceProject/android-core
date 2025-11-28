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

class MockBoundlessService : BoundlessServiceType {

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
    TODO("Not yet implemented")
  }

  override fun fulfillEPUB(
    httpClient: LSHTTPClientType,
    link: BoundlessCMTemplatedLink,
    credentials: LSHTTPAuthorizationType?,
    outputFile: File,
    outputLicenseFile: File,
    isCancelled: () -> Boolean,
    onDownloadEvent: (LSHTTPDownloadState) -> Unit
  ): DRMTaskResult<BoundlessFulfilledCMEPUB> {
    TODO("Not yet implemented")
  }

  override fun keyPair(): KeyPair {
    TODO("Not yet implemented")
  }
}
