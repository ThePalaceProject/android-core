package org.nypl.simplified.tests.mocking

import android.app.Application
import kotlinx.coroutines.DelicateCoroutinesApi
import org.mockito.Mockito
import org.readium.r2.lcp.LcpAuthenticating
import org.readium.r2.lcp.LcpError
import org.readium.r2.lcp.LcpLicense
import org.readium.r2.lcp.LcpService
import org.readium.r2.lcp.license.model.LicenseDocument
import org.readium.r2.shared.publication.protection.ContentProtection
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.ContainerAsset
import java.io.File

class MockLCPService(
  val context: Application,
  val publication: LcpService.AcquiredPublication? = null
) : LcpService {

  @Deprecated(
    "Use a LcpPublicationRetriever instead.",
    replaceWith = ReplaceWith("publicationRetriever()"),
    level = DeprecationLevel.ERROR
  )
  override suspend fun acquirePublication(
    lcpl: File,
    onProgress: (Double) -> Unit
  ): Try<LcpService.AcquiredPublication, LcpError> {
    return if (publication == null) {
      Try.failure(LcpError.LicenseProfileNotSupported)
    } else {
      Try.success(publication)
    }
  }

  override suspend fun acquirePublication(
    lcpl: ByteArray,
    onProgress: (Double) -> Unit
  ): Try<LcpService.AcquiredPublication, LcpError> {
    TODO()
  }

  override fun contentProtection(
    authentication: LcpAuthenticating
  ): ContentProtection {
    return Mockito.mock(ContentProtection::class.java)
  }

  override suspend fun injectLicenseDocument(
    licenseDocument: LicenseDocument,
    publicationFile: File
  ): Try<Unit, LcpError> {
    return Try.success(Unit)
  }

  @Deprecated(
    "Use `retrieveLicense()` with coroutines instead",
    replaceWith = ReplaceWith("retrieveLicense(File(publication), authentication, allowUserInteraction = true)"),
    level = DeprecationLevel.ERROR
  )
  @DelicateCoroutinesApi
  override fun retrieveLicense(
    publication: String,
    authentication: LcpAuthenticating?,
    completion: (LcpLicense?, LcpError?) -> Unit
  ) {
    completion.invoke(null, LcpError.LicenseProfileNotSupported)
  }

  override suspend fun retrieveLicense(
    asset: Asset,
    authentication: LcpAuthenticating,
    allowUserInteraction: Boolean
  ): Try<LcpLicense, LcpError> {
    return Try.failure(LcpError.LicenseProfileNotSupported)
  }

  override suspend fun retrieveLicenseDocument(
    asset: ContainerAsset
  ): Try<LicenseDocument, LcpError> {
    return Try.failure(LcpError.LicenseProfileNotSupported)
  }

  @Deprecated(
    "Use an AssetSniffer and check the conformance of the returned format to LcpSpecification",
    level = DeprecationLevel.ERROR
  )
  override suspend fun isLcpProtected(
    file: File
  ): Boolean {
    return false
  }
}
