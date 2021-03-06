package org.nypl.simplified.books.api

import org.nypl.drm.core.AdobeAdeptLoan
import java.io.File
import java.io.Serializable

/**
 * The `BookDRMInformation` class represents an immutable snapshot of the current DRM
 * information associated with a book.
 */

sealed class BookDRMInformation : Serializable {

  /**
   * The kind of DRM
   */

  abstract val kind: BookDRMKind

  /**
   * The Adobe ACS information associated with a book.
   */

  data class ACS(

    /**
     * The ACSM file. This is only present if an attempt has been made to fulfill the book.
     */

    val acsmFile: File?,

    /**
     * The rights information. This is only present if the book has been fulfilled.
     */

    val rights: Pair<File, AdobeAdeptLoan>?
  ) : BookDRMInformation() {
    override val kind: BookDRMKind = BookDRMKind.ACS
  }

  /**
   * The LCP information associated with a book.
   */

  data class LCP(

    /**
     * The hashed LCP passphrase for the book.
     */

    val hashedPassphrase: String?
  ) : BookDRMInformation() {
    override val kind: BookDRMKind = BookDRMKind.LCP
  }

  /**
   * The AXIS information associated with a book.
   */

  data class AXIS(

    /**
     * The license file. This is only present if an attempt has been made to fulfill the book.
     */

    val license: File?,

    /**
     * The file containing the key used to fulfill the book. This is only present
     * if an attempt has been made to fulfill the book.
     */

    val userKey: File?
  ) : BookDRMInformation() {
    override val kind: BookDRMKind = BookDRMKind.AXIS
  }

  /**
   * The book either has no DRM, or uses some kind of external DRM system that the book database
   * doesn't know about (such as proprietary AudioBook DRM).
   */

  object None : BookDRMInformation() {
    override val kind: BookDRMKind = BookDRMKind.NONE
  }
}
