package org.nypl.simplified.books.borrowing

import java.net.URI

/**
 * The credentials that should be used in a subtask. The majority of the time, subtasks will
 * simply use credentials from the current account. There are cases, however, where a subtask
 * will prepare credentials for the next subtask. A good example of this is LS bearer tokens.
 */

sealed class BorrowSubtaskCredentials {

  data object UseAccountCredentials : BorrowSubtaskCredentials()

  data class UseBearerToken(
    val refreshURI: URI,
    val token: String
  ) : BorrowSubtaskCredentials()
}
