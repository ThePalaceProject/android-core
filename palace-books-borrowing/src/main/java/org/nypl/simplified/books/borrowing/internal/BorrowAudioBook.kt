package org.nypl.simplified.books.borrowing.internal

import com.io7m.junreachable.UnreachableCodeException
import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAPassword
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAUsernamePassword
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.api.AccountPassword
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.accounts.api.AccountUsername
import org.nypl.simplified.books.audio.AudioBookLink
import org.nypl.simplified.books.audio.AudioBookManifestRequest
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleEPUB
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandlePDF
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.internal.BorrowErrorCodes.audioStrategyFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException.BorrowSubtaskFailed
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.links.Link
import org.nypl.simplified.taskrecorder.api.TaskResult
import java.io.File
import java.net.URI

/**
 * A task that copies content from `content://` URIs and bundled resources.
 */

class BorrowAudioBook private constructor() : BorrowSubtaskType {

  companion object : BorrowSubtaskFactoryType {
    override val name: String
      get() = "Audio Book"

    override fun createSubtask(): BorrowSubtaskType {
      return BorrowAudioBook()
    }

    override fun isApplicableFor(
      type: MIMEType,
      target: Link?,
      account: AccountReadableType?,
      remaining: List<MIMEType>
    ): Boolean {
      for (audioType in StandardFormatNames.allAudioBooks) {
        if (MIMECompatibility.isCompatibleStrictWithoutAttributes(type, audioType)) {
          return true
        }
      }
      return false
    }
  }

  override fun execute(context: BorrowContextType) {
    context.taskRecorder.beginNewStep("Downloading audio book...")
    context.bookDownloadIsRunning(
      "Requesting download...",
      receivedSize = 0L,
      expectedSize = 100L,
      bytesPerSecond = 0L
    )

    return try {
      val currentURI = context.currentURICheck()
      context.taskRecorder.addAttribute("URI", currentURI.toString())

      val downloaded = this.runStrategy(context, currentURI)
      try {
        this.saveDownloadedContent(context, downloaded)
      } finally {
        downloaded.file.delete()
      }
    } catch (e: BorrowSubtaskFailed) {
      context.bookDownloadFailed()
      throw e
    }
  }

  private data class DownloadedManifest(
    val file: File,
    val sourceURI: URI
  )

  /**
   * @return `true` if the request content type implies an Overdrive audio book
   */

  private fun isOverdrive(
    currentLink: Link
  ): Boolean {
    val type = currentLink.type ?: return false

    return BookFormats.audioBookOverdriveMimeTypes()
      .map { it.fullType }
      .contains(type.fullType)
  }

  private fun runStrategy(
    context: BorrowContextType,
    currentURI: URI
  ): DownloadedManifest {
    context.taskRecorder.beginNewStep("Executing audio book manifest strategy...")

    val customCredentials =
      if (this.isOverdrive(context.currentLinkCheck())) {
        when (val credentials = context.takeSubtaskCredentialsRequiringAccount()) {
          is AccountAuthenticationCredentials.Basic -> {
            opaCredentialsOf(credentials.userName, credentials.password)
          }

          is AccountAuthenticationCredentials.BasicToken -> {
            opaCredentialsOf(credentials.userName, credentials.password)
          }

          null,
          is AccountAuthenticationCredentials.OAuthWithIntermediary,
          is AccountAuthenticationCredentials.SAML2_0 -> {
            null
          }
        }
      } else {
        null
      }

    val strategy =
      context.audioBookManifestStrategies.createStrategy(
        context = context.application,
        AudioBookManifestRequest(
          cacheDirectory = context.cacheDirectory(),
          contentType = context.currentAcquisitionPathElement.mimeType,
          authorizationHandler = context.audiobookAuthorizationHandler,
          httpClient = context.httpClient,
          palaceID = PlayerPalaceID(context.bookCurrent.entry.id),
          services = context.services,
          target = AudioBookLink.Manifest(currentURI),
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
          context.taskRecorder.currentStepSucceeded("Strategy succeeded.")
          context.taskRecorder.addAll(result.steps)
          context.taskRecorder.addAttributes(result.attributes)
          context.taskRecorder.beginNewStep("Checking AudioBook strategy result…")

          val outputFile = File.createTempFile("manifest", "data", context.cacheDirectory())
          outputFile.writeBytes(result.result.fulfilled.data)
          DownloadedManifest(
            file = outputFile,
            sourceURI = currentURI
          )
        }

        is TaskResult.Failure -> {
          val exception = BorrowSubtaskFailed()
          context.taskRecorder.currentStepFailed(
            message = "Strategy failed.",
            errorCode = audioStrategyFailed,
            exception = exception,
            extraMessages = listOf()
          )
          context.taskRecorder.addAll(result.steps)
          context.taskRecorder.addAttributes(result.attributes)
          throw exception
        }
      }
    } finally {
      subscription.dispose()
    }
  }

  private fun opaCredentialsOf(
    userName: AccountUsername,
    password: AccountPassword
  ): OPAUsernamePassword {
    val password = if (password.value.isBlank()) {
      OPAPassword.NotRequired
    } else {
      OPAPassword.Password(password.value)
    }
    return OPAUsernamePassword(
      userName = userName.value,
      password = password
    )
  }

  private fun saveDownloadedContent(
    context: BorrowContextType,
    data: DownloadedManifest
  ) {
    context.taskRecorder.beginNewStep("Saving book...")
    val formatHandle = context.bookDatabaseEntry.findFormatHandleForContentType(
      contentType = context.currentAcquisitionPathElement.mimeType
    )

    return when (formatHandle) {
      is BookDatabaseEntryFormatHandleAudioBook -> {
        formatHandle.copyInManifestAndURI(
          data = data.file.readBytes(),
          manifestURI = data.sourceURI
        )
        context.bookDownloadSucceeded()
      }

      is BookDatabaseEntryFormatHandlePDF,
      is BookDatabaseEntryFormatHandleEPUB,
      null -> {
        throw UnreachableCodeException()
      }
    }
  }
}
