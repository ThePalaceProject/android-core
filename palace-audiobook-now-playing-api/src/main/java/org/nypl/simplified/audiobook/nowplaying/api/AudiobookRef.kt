package org.nypl.simplified.audiobook.nowplaying.api

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookID

/**
 * A reference to an audiobook: the account that owns it and its registry ID.
 *
 * The two IDs are enough to identify the book again: its data (formats, OPDS entry) can be
 * re-derived from the book registry given the [bookID].
 */

data class AudiobookRef(
  val accountID: AccountID,
  val bookID: BookID,
)
