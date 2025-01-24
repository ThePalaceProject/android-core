package org.librarysimplified.viewer.audiobook

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import java.io.Serializable
import java.net.URI

/**
 * Parameters for the audio book player.
 */

class AudioBookPlayerParameters(

  /**
   * The user agent string used to make manifest requests.
   */

  val userAgent: String,

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
