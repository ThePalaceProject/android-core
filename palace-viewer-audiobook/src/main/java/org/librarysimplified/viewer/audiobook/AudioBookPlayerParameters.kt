package org.librarysimplified.viewer.audiobook

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import java.io.Serializable
import java.net.URI
import java.util.UUID

/**
 * Parameters for the audiobook player.
 */

class AudioBookPlayerParameters(
  /**
   * The unique player ID that will be used to identity the created player instance.
   */

  val playerID: UUID,

  /**
   * The account to which the book belongs.
   */

  val accountID: AccountID,

  /**
   * The account provider to which the book belongs.
   */

  val accountProviderID: URI,

  /**
   * The book ID.
   */

  val bookID: BookID,

  /**
   * The OPDS entry for the book.
   */

  val opdsEntry: OPDSAcquisitionFeedEntry,

  /**
   * The DRM information for the book.
   */

  val drmInfo: BookDRMInformation,
) : Serializable
