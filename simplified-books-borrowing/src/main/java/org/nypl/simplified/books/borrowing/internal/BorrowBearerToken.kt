package org.nypl.simplified.books.borrowing.internal

import one.irradia.mime.api.MIMECompatibility
import one.irradia.mime.api.MIMEType
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.books.borrowing.BorrowContextType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskException
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskFactoryType
import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskType
import org.nypl.simplified.books.formats.api.StandardFormatNames
import org.nypl.simplified.links.Link

/**
 * A task that pretends to negotiate a Simplified bearer token. Bearer token handling is
 * transparent in the HTTP client now, so this task trivially succeeds.
 */

class BorrowBearerToken : BorrowSubtaskType {

  companion object : BorrowSubtaskFactoryType {
    override val name: String
      get() = "Bearer Token Negotiation"

    override fun createSubtask(): BorrowSubtaskType {
      return BorrowBearerToken()
    }

    override fun isApplicableFor(
      type: MIMEType,
      target: Link?,
      account: AccountReadableType?,
      remaining: List<MIMEType>
    ): Boolean {
      return MIMECompatibility.isCompatibleStrictWithoutAttributes(
        type,
        StandardFormatNames.simplifiedBearerToken
      )
    }
  }

  override fun execute(context: BorrowContextType) {
    context.taskRecorder.beginNewStep("Handling bearer token negotiation...")
    context.bookDownloadIsRunning("Requesting download...", receivedSize = 0L)

    return try {
      val currentURI = context.currentLinkCheck()
      context.receivedNewURI(currentURI)
      context.taskRecorder.currentStepSucceeded("Bearer token negotiation is transparent.")
      Unit
    } catch (e: BorrowSubtaskException.BorrowSubtaskFailed) {
      context.bookDownloadFailed()
      throw e
    }
  }
}
