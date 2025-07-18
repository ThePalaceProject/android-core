package org.nypl.simplified.books.borrowing

import android.app.Application
import org.joda.time.Instant
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.services.api.ServiceDirectoryType
import org.nypl.drm.core.AdobeAdeptExecutorType
import org.nypl.drm.core.BoundlessServiceType
import org.nypl.simplified.books.audio.AudioBookManifestStrategiesType
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskDirectoryType
import org.nypl.simplified.books.bundled.api.BundledContentResolverType
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.content.api.ContentResolverType
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.readium.r2.lcp.LcpService
import java.io.File

/**
 * The services required by borrow tasks.
 */

data class BorrowRequirements(
  val application: Application,
  val adobeExecutor: AdobeAdeptExecutorType?,
  val audioBookManifestStrategies: AudioBookManifestStrategiesType,
  val boundlessService: BoundlessServiceType?,
  val bookFormatSupport: BookFormatSupportType,
  val bookRegistry: BookRegistryType,
  val bundledContent: BundledContentResolverType,
  val cacheDirectory: File,
  val clock: () -> Instant,
  val contentResolver: ContentResolverType,
  val httpClient: LSHTTPClientType,
  val lcpService: LcpService?,
  val profiles: ProfilesDatabaseType,
  val services: ServiceDirectoryType,
  val subtasks: BorrowSubtaskDirectoryType,
  val temporaryDirectory: File
) {

  companion object {
    fun create(
      services: ServiceDirectoryType,
      application: Application,
      clock: () -> Instant,
      cacheDirectory: File,
      temporaryDirectory: File
    ): BorrowRequirements {
      return BorrowRequirements(
        application = application,
        adobeExecutor = services.optionalService(AdobeAdeptExecutorType::class.java),
        audioBookManifestStrategies = services.requireService(AudioBookManifestStrategiesType::class.java),
        boundlessService = services.optionalService(BoundlessServiceType::class.java),
        bookFormatSupport = services.requireService(BookFormatSupportType::class.java),
        bookRegistry = services.requireService(BookRegistryType::class.java),
        bundledContent = services.requireService(BundledContentResolverType::class.java),
        cacheDirectory = cacheDirectory,
        clock = clock,
        contentResolver = services.requireService(ContentResolverType::class.java),
        httpClient = services.requireService(LSHTTPClientType::class.java),
        lcpService = services.optionalService(LcpService::class.java),
        profiles = services.requireService(ProfilesDatabaseType::class.java),
        services = services,
        subtasks = services.requireService(BorrowSubtaskDirectoryType::class.java),
        temporaryDirectory = temporaryDirectory
      )
    }
  }
}
