package org.nypl.simplified.tests.mocking

import org.readium.r2.lcp.LcpAuthenticating
import org.readium.r2.lcp.LcpException
import org.readium.r2.lcp.LcpLicense
import org.readium.r2.lcp.LcpService
import org.readium.r2.shared.util.Try
import java.io.File

class MockLCPService(
  val publication: LcpService.AcquiredPublication? = null
) : LcpService {
  override suspend fun acquirePublication(
    lcpl: ByteArray,
    onProgress: (Double) -> Unit
  ): Try<LcpService.AcquiredPublication, LcpException> {
    return if (publication == null) {
      Try.failure(LcpException.LicenseProfileNotSupported)
    } else {
      Try.success(publication)
    }
  }

  override suspend fun isLcpProtected(
    file: File
  ): Boolean {
    return false
  }

  override suspend fun retrieveLicense(
    file: File,
    authentication: LcpAuthenticating,
    allowUserInteraction: Boolean,
    sender: Any?
  ): Try<LcpLicense, LcpException> {
    return Try.failure(LcpException.LicenseProfileNotSupported)
  }
}
