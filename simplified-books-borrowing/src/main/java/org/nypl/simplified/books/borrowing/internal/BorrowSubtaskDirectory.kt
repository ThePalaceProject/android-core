package org.nypl.simplified.books.borrowing.internal

import org.nypl.simplified.books.borrowing.subtasks.BorrowSubtaskDirectoryType

/**
 * The default implementation of the [BorrowSubtaskDirectoryType] interface.
 */

class BorrowSubtaskDirectory : BorrowSubtaskDirectoryType {
  override val subtasks =
    listOf(
      BorrowACSM,
      BorrowAudioBook,
      BorrowAxisNow,
      BorrowBearerToken,
      BorrowCopy,
      // BorrowSAMLDownload must precede BorrowDirectDownload in precedence.
      BorrowSAMLDownload,
      BorrowDirectDownload,
      BorrowLCP,
      BorrowLoanCreate
    )
}
