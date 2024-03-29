package org.nypl.simplified.bookmarks.api

import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.database.api.AccountType
import java.io.IOException
import java.net.URI

interface BookmarkHTTPCallsType {

  /**
   * Retrieve the list of bookmarks for the given account. This call will fail
   * with an exception if syncing is not enabled.
   *
   * @see #syncingIsEnabled
   * @see #syncingEnable
   */

  @Throws(IOException::class)
  fun bookmarksGet(
    account: AccountType,
    annotationsURI: URI,
    credentials: AccountAuthenticationCredentials
  ): List<BookmarkAnnotation>

  /**
   * Add a bookmark for the given account. This call will fail with an exception if
   * syncing is not enabled.
   *
   * @see #syncingIsEnabled
   * @see #syncingEnable
   */

  @Throws(IOException::class)
  fun bookmarkAdd(
    account: AccountType,
    annotationsURI: URI,
    credentials: AccountAuthenticationCredentials,
    bookmark: BookmarkAnnotation
  ): URI?

  /**
   * Delete a bookmark for the given account. This call will fail with an exception if
   * syncing is not enabled.
   *
   * @see #syncingIsEnabled
   * @see #syncingEnable
   */

  @Throws(IOException::class)
  fun bookmarkDelete(
    account: AccountType,
    bookmarkURI: URI,
    credentials: AccountAuthenticationCredentials
  ): Boolean
}
