package org.nypl.simplified.books.borrowing.internal

import com.io7m.junreachable.UnreachableCodeException
import kotlinx.coroutines.runBlocking
import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCancelled
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadCompletedSuccessfully
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedExceptionally
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedServer
import org.librarysimplified.http.downloads.LSHTTPDownloadState.LSHTTPDownloadResult.DownloadFailed.DownloadFailedUnacceptableMIME
import org.librarysimplified.http.downloads.LSHTTPDownloads
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.book_database.BookDRMInformationHandleLCP
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.internal.BorrowLCPSupport.fetchAllR2ErrorMessages
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskCancelled
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskHaltedEarly
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.books.formats.api.StandardFormatNames.lcpLicenseFiles
import org.nypl.simplified.links.Link
import org.readium.r2.lcp.license.model.LicenseDocument
import org.readium.r2.shared.util.ErrorException
import org.readium.r2.shared.util.Try
import java.io.File
import java.io.IOException
import java.net.URI

class BorrowLCPPDF : BorrowSubtaskType {

  companion object : BorrowSubtaskFactoryType {
    override val name: String
      get() = "LCP PDF Download"

    override fun createSubtask(): BorrowSubtaskType {
      return BorrowLCPPDF()
    }

    override fun isApplicableFor(
      type: MIMEType,
      target: Link?,
      account: AccountReadableType?,
      remaining: List<MIMEType>
    ): Boolean {
      return if (MIMECompatibility.isCompatibleStrictWithoutAttributes(type, lcpLicenseFiles)) {
        val next = remaining.firstOrNull()
        if (next != null) {
          MIMECompatibility.isCompatibleStrictWithoutAttributes(next, StandardFormatNames.genericPDFFiles)
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

      val passphrase =
        BorrowLCPSupport.findPassphraseOrManual(context)
      val licenseBytes =
        BorrowLCPSupport.downloadLicense(context, context.currentURICheck())

      this.fulfill(context, licenseBytes, passphrase)
    } catch (e: BorrowSubtaskFailed) {
      context.bookDownloadFailed()
      throw e
    }
  }

  private fun fulfill(
    context: BorrowContextType,
    licenseBytes: ByteArray,
    passphrase: String
  ) {
    context.taskRecorder.beginNewStep("Fulfilling book...")
    context.checkCancelled()

    val fulfillmentMimeType =
      context.opdsAcquisitionPath.asMIMETypes().last()
    val temporaryFile =
      context.temporaryFile("pdf")

    try {
      val license =
        when (val r = LicenseDocument.fromBytes(licenseBytes)) {
          is Try.Failure -> throw ErrorException(r.value)
          is Try.Success -> r.value
        }

      val link =
        license.link(LicenseDocument.Rel.Publication)
      val url =
        URI.create(
          link
            ?.url()
            ?.toString()
            ?: throw IOException("Unparseable license link ($link)")
        )

      val downloadRequest =
        BorrowHTTP.createDownloadRequest(
          context = context,
          target = url,
          outputFile = temporaryFile,
          requestModifier = { properties ->
            properties.copy(
              authorization = null
            )
          },
          expectedTypes = hashSetOf(
            fulfillmentMimeType,
            // Sometimes fulfillment servers will set the content type to generic zip or octet
            // stream, so these are acceptable too.
            StandardFormatNames.genericZIPFiles,
            MIMECompatibility.applicationOctetStream
          )
        )

      when (val result = LSHTTPDownloads.download(downloadRequest)) {
        DownloadCancelled ->
          throw BorrowSubtaskCancelled()

        is DownloadFailedServer ->
          throw BorrowHTTP.onDownloadFailedServer(context, result)

        is DownloadFailedUnacceptableMIME ->
          throw BorrowSubtaskFailed()

        is DownloadFailedExceptionally ->
          throw BorrowHTTP.onDownloadFailedExceptionally(context, result)

        is DownloadCompletedSuccessfully -> {
          this.installLicense(context, temporaryFile, licenseBytes)
          this.saveFulfilledBook(context, temporaryFile, passphrase, licenseBytes)
          context.bookDownloadSucceeded()
        }
      }
    } catch (e: BorrowSubtaskFailed) {
      context.bookDownloadFailed()
      throw e
    } catch (e: Exception) {
      context.taskRecorder.currentStepFailed(
        message = "LCP fulfillment error: ${e.message}",
        errorCode = BorrowErrorCodes.lcpFulfillmentFailed,
        exception = e,
        extraMessages = listOf()
      )
      throw BorrowSubtaskFailed()
    } finally {
      temporaryFile.delete()
    }

    /*
     * LCP is a special case in the sense that it supersedes any acquisition
     * path elements that might follow this one. We mark this subtask as having halted
     * early.
     */

    throw BorrowSubtaskHaltedEarly()
  }

  /**
   * Install the license into the fulfilled book.
   */

  private fun installLicense(
    context: BorrowContextType,
    bookFile: File,
    licenseBytes: ByteArray
  ) {
    context.taskRecorder.beginNewStep("Installing license...")

    val lcpService =
      context.lcpService ?: throw UnreachableCodeException()

    runBlocking {
      when (val r = LicenseDocument.fromBytes(licenseBytes)) {
        is Try.Failure -> {
          context.taskRecorder.currentStepFailed(
            message = "Unparseable LCP license.",
            errorCode = "errorLCPLicense",
            extraMessages = fetchAllR2ErrorMessages(r)
          )
          context.taskRecorder.addAttribute("Parse Error", r.value.message)
          context.bookDownloadFailed()
          throw BorrowSubtaskFailed()
        }

        is Try.Success -> lcpService.injectLicenseDocument(r.value, bookFile)
      }
    }

    context.taskRecorder.currentStepSucceeded("License installed.")
  }

  private fun saveFulfilledBook(
    context: BorrowContextType,
    bookFile: File,
    passphrase: String,
    licenseBytes: ByteArray
  ) {
    context.taskRecorder.beginNewStep("Saving fulfilled book...")

    val formatHandle = this.findFormatHandle(context)
    formatHandle.setDRMKind(BookDRMKind.LCP)

    val drmHandle = formatHandle.drmInformationHandle as BookDRMInformationHandleLCP
    drmHandle.setInfo(passphrase, licenseBytes)

    when (formatHandle) {
      is BookDatabaseEntryFormatHandlePDF -> {
        formatHandle.copyInBook(bookFile)
      }

      is BookDatabaseEntryFormatHandleEPUB,
      is BookDatabaseEntryFormatHandleAudioBook -> {
        throw UnreachableCodeException()
      }
    }

    context.taskRecorder.currentStepSucceeded("Saved book.")
  }

  /**
   * Determine the actual book format we're aiming for at the end of the acquisition path.
   */

  private fun findFormatHandle(
    context: BorrowContextType
  ): BookDatabaseEntryFormatHandle {
    val eventualType = context.opdsAcquisitionPath.asMIMETypes().last()
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
