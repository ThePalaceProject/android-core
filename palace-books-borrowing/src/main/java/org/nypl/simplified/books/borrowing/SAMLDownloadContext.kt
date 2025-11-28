package org.nypl.simplified.books.borrowing

import java.net.URI

data class SAMLDownloadContext(

  /**
   * Indicates if the required SAML authentication for the download is complete.
   */

  val isSAMLAuthComplete: Boolean = false,

  /**
   * The original URI used to download the book, which may be interrupted by a login/approval form
   * from the IdP. Typically, this is the fulfillment URL from the feed.
   */

  val downloadURI: URI,

  /**
   * The URI from which to download the book when authentication is complete. Typically, this
   * is the URL to which the browser is redirected, after a submitting a login form presented by
   * the IdP when attempting to retrieve the original download URI.
   */

  val authCompleteDownloadURI: URI
)
