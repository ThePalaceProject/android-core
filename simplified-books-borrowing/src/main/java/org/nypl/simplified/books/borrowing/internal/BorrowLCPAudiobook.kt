package org.nypl.simplified.books.borrowing.internal

import com.io7m.junreachable.UnreachableCodeException
import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.audio.AudioBookLink
import org.nypl.simplified.books.audio.AudioBookManifestRequest
import org.nypl.simplified.books.book_database.BookDRMInformationHandleLCP
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.audioStrategyFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.StandardFormatNames.lcpAudioBooks
import org.nypl.simplified.books.formats.api.StandardFormatNames.lcpLicenseFiles
import org.nypl.simplified.taskrecorder.api.TaskResult
import java.io.File
import java.net.URI

class BorrowLCPAudiobook : BorrowSubtaskType {

  companion object : BorrowSubtaskFactoryType {
    override val name: String
      get() = "LCP AudioBook Download"

    override fun createSubtask(): BorrowSubtaskType {
      return BorrowLCPAudiobook()
    }

    override fun isApplicableFor(
      type: MIMEType,
      target: URI?,
      account: AccountReadableType?,
      remaining: List<MIMEType>
    ): Boolean {
      return if (MIMECompatibility.isCompatibleStrictWithoutAttributes(type, lcpLicenseFiles)) {
        val next = remaining.firstOrNull()
        if (next != null) {
          MIMECompatibility.isCompatibleStrictWithoutAttributes(next, lcpAudioBooks)
        } else {
          false
        }
      } else {
        false
      }
    }
  }

  override fun execute(
    context: BorrowContextType
  ) {
    try {
      BorrowLCPSupport.checkDRMSupport(context)
      val passphrase = BorrowLCPSupport.findPassphraseOrManual(context)
      val downloaded = this.fulfill(context, passphrase)
      this.saveFulfilledBook(context, passphrase, downloaded)
      throw BorrowSubtaskException.BorrowSubtaskHaltedEarly()
    } catch (e: BorrowSubtaskFailed) {
      context.bookDownloadFailed()
      throw e
    }
  }

  private data class DownloadedManifest(
    val manifestURI: URI?,
    val manifestData: ByteArray,
    val licenseData: ByteArray
  )

  private fun fulfill(
    context: BorrowContextType,
    passphrase: String
  ): DownloadedManifest {
    context.taskRecorder.beginNewStep("Executing audio book manifest strategy...")

    val strategy =
      context.audioBookManifestStrategies.createStrategy(
        context = context.application,
        AudioBookManifestRequest(
          cacheDirectory = context.cacheDirectory(),
          contentType = context.currentAcquisitionPathElement.mimeType,
          credentials = context.account.loginState.credentials,
          httpClient = context.httpClient,
          services = context.services,
          target = AudioBookLink.License(context.currentURICheck()),
          palaceID = PlayerPalaceID(context.bookCurrent.entry.id),
          userAgent = PlayerUserAgent(context.httpClient.userAgent()),
        )
      )

    val subscription =
      strategy.events.subscribe { message ->
        context.bookDownloadIsRunning(
          message = message,
          receivedSize = 50L,
          expectedSize = 100L,
          bytesPerSecond = 0L
        )
      }

    return try {
      when (val result = strategy.execute()) {
        is TaskResult.Success -> {
          val licenseBytes = result.result.licenseBytes
          if (licenseBytes == null) {
            val exception = BorrowSubtaskFailed()
            context.taskRecorder.addAll(result.steps)
            context.taskRecorder.addAttributes(result.attributes)
            context.taskRecorder.beginNewStep("Checking audiobook strategy result...")
            context.taskRecorder.currentStepFailed(
              "Download succeeded, but no LCP license was provided!",
              audioStrategyFailed,
              exception = exception,
              extraMessages = listOf()
            )
            throw exception
          }

          context.taskRecorder.addAll(result.steps)
          context.taskRecorder.addAttributes(result.attributes)
          context.taskRecorder.beginNewStep("Checking audiobook strategy result...")
          context.taskRecorder.currentStepSucceeded("Strategy succeeded.")

          val outputFile = File.createTempFile("manifest", "data", context.cacheDirectory())
          outputFile.writeBytes(result.result.fulfilled.data)
          DownloadedManifest(
            manifestURI = result.result.fulfilled.source,
            manifestData = result.result.fulfilled.data,
            licenseData = licenseBytes
          )
        }

        is TaskResult.Failure -> {
          val exception = BorrowSubtaskFailed()
          context.taskRecorder.addAll(result.steps)
          context.taskRecorder.addAttributes(result.attributes)
          context.taskRecorder.beginNewStep("Checking AudioBook strategy result…")
          context.taskRecorder.currentStepFailed(
            message = "Strategy failed.",
            errorCode = audioStrategyFailed,
            exception = exception,
            extraMessages = listOf()
          )
          throw exception
        }
      }
    } finally {
      subscription.dispose()
    }
  }

  private fun saveFulfilledBook(
    context: BorrowContextType,
    passphrase: String,
    manifest: DownloadedManifest
  ) {
    context.taskRecorder.beginNewStep("Saving fulfilled book...")

    val formatHandle = this.findFormatHandle(context)
    formatHandle.setDRMKind(BookDRMKind.LCP)

    val drmHandle = formatHandle.drmInformationHandle as BookDRMInformationHandleLCP
    drmHandle.setInfo(passphrase, manifest.licenseData)

    when (formatHandle) {
      is BookDatabaseEntryFormatHandleAudioBook -> {
        formatHandle.copyInManifestAndURI(
          data = manifest.manifestData,
          manifestURI = manifest.manifestURI
        )
        context.taskRecorder.currentStepSucceeded("Saved book.")
        context.bookDownloadSucceeded()
      }

      is BookDatabaseEntryFormatHandleEPUB,
      is BookDatabaseEntryFormatHandlePDF ->
        throw UnreachableCodeException()
    }

    context.taskRecorder.currentStepSucceeded("Saved book.")
  }

  /**
   * Determine the actual book format we're aiming for at the end of the acquisition path.
   */

  private fun findFormatHandle(
    context: BorrowContextType
  ): BookDatabaseEntryFormatHandle {
    val eventualType = lcpAudioBooks
    val formatHandle = context.bookDatabaseEntry.findFormatHandleForContentType(eventualType)
    if (formatHandle == null) {
      context.taskRecorder.currentStepFailed(
        message = "No format handle available for ${eventualType.fullType}",
        errorCode = BorrowErrorCodes.noFormatHandle,
        extraMessages = listOf()
      )
      throw BorrowSubtaskFailed()
    }
    return formatHandle
  }
}
