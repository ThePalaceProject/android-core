package org.nypl.simplified.tests.books.accounts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.librarysimplified.http.api.LSHTTPProblemReport
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.Handled401

class AccountAuthenticatedHTTPTest {

  @Test
  fun test401_0() {
    assertEquals(
      Handled401.ErrorIsUnrecoverable,
      AccountAuthenticatedHTTP.handle401Error(null)
    )
  }

  @Test
  fun test401_1() {
    assertEquals(
      Handled401.ErrorIsUnrecoverable,
      AccountAuthenticatedHTTP.handle401Error(
        LSHTTPProblemReport(
          status = null,
          title = null,
          detail = null,
          type = null
        )
      )
    )
  }

  @Test
  fun test401_2() {
    assertEquals(
      Handled401.ErrorIsRecoverableCredentialsExpired,
      AccountAuthenticatedHTTP.handle401Error(
        LSHTTPProblemReport(
          status = null,
          title = null,
          detail = null,
          type = "http://palaceproject.io/terms/problem/auth/recoverable/ouch"
        )
      )
    )
  }

  @Test
  fun test401_3() {
    assertEquals(
      Handled401.ErrorIsUnrecoverable,
      AccountAuthenticatedHTTP.handle401Error(
        LSHTTPProblemReport(
          status = null,
          title = null,
          detail = null,
          type = "http://palaceproject.io/terms/problem/auth/unrecoverable/ouch"
        )
      )
    )
  }

  @Test
  @Deprecated("Server will stop serving these problem types soon.")
  fun test401_4() {
    assertEquals(
      Handled401.ErrorIsRecoverableCredentialsExpired,
      AccountAuthenticatedHTTP.handle401Error(
        LSHTTPProblemReport(
          status = null,
          title = null,
          detail = null,
          type = "http://librarysimplified.org/terms/problem/"
        )
      )
    )
  }

  /**
   * A blocked patron whose library turned the standard title off sees only the message the
   * library wrote. This is the case the `show_title` extension exists for.
   */

  @Test
  fun testProblemReportMessage_ShowTitleFalse_SuppressesTitle() {
    assertEquals(
      "Please sign in at your local library instead.",
      AccountAuthenticatedHTTP.problemReportMessage(
        problemReport = LSHTTPProblemReport(
          status = 403,
          title = "Blocked by library policy.",
          detail = "Please sign in at your local library instead.",
          type = "http://librarysimplified.org/terms/problem/credentials-blocked-by-policy",
          showTitle = false
        ),
        fallback = "Server error: 403 Forbidden"
      )
    )
  }

  /**
   * The default, and what every problem report that predates the extension does: the title
   * frames the message.
   */

  @Test
  fun testProblemReportMessage_ShowTitleDefault_KeepsTitle() {
    assertEquals(
      "Blocked by library policy.\n\nPlease sign in at your local library instead.",
      AccountAuthenticatedHTTP.problemReportMessage(
        problemReport = LSHTTPProblemReport(
          status = 403,
          title = "Blocked by library policy.",
          detail = "Please sign in at your local library instead.",
          type = "http://librarysimplified.org/terms/problem/credentials-blocked-by-policy"
        ),
        fallback = "Server error: 403 Forbidden"
      )
    )
  }

  /**
   * An explicit `show_title: true` is equivalent to omitting the member.
   */

  @Test
  fun testProblemReportMessage_ShowTitleTrue_KeepsTitle() {
    assertEquals(
      "A title.\n\nA detail.",
      AccountAuthenticatedHTTP.problemReportMessage(
        problemReport = LSHTTPProblemReport(
          status = 403,
          title = "A title.",
          detail = "A detail.",
          type = null,
          showTitle = true
        ),
        fallback = "fallback"
      )
    )
  }

  /**
   * No report at all: the caller's own message is used, exactly as before this function
   * existed.
   */

  @Test
  fun testProblemReportMessage_NoReport_UsesFallback() {
    assertEquals(
      "Server error: 500 Internal Server Error",
      AccountAuthenticatedHTTP.problemReportMessage(
        problemReport = null,
        fallback = "Server error: 500 Internal Server Error"
      )
    )
  }

  /**
   * A report carrying no usable `detail` has nothing worth showing a patron, so the caller's
   * message stands. `show_title` is irrelevant here: suppressing a title must never leave the
   * user staring at an empty string.
   */

  @Test
  fun testProblemReportMessage_NoDetail_UsesFallback() {
    assertEquals(
      "Server error: 403 Forbidden",
      AccountAuthenticatedHTTP.problemReportMessage(
        problemReport = LSHTTPProblemReport(
          status = 403,
          title = "Blocked by library policy.",
          detail = null,
          type = null,
          showTitle = false
        ),
        fallback = "Server error: 403 Forbidden"
      )
    )
  }

  @Test
  fun testProblemReportMessage_BlankDetail_UsesFallback() {
    assertEquals(
      "Server error: 403 Forbidden",
      AccountAuthenticatedHTTP.problemReportMessage(
        problemReport = LSHTTPProblemReport(
          status = 403,
          title = "Blocked by library policy.",
          detail = "   ",
          type = null
        ),
        fallback = "Server error: 403 Forbidden"
      )
    )
  }

  /**
   * A report with a usable detail but no title renders the detail alone rather than gluing
   * blank framing above it.
   */

  @Test
  fun testProblemReportMessage_NoTitle_RendersDetailAlone() {
    assertEquals(
      "A detail.",
      AccountAuthenticatedHTTP.problemReportMessage(
        problemReport = LSHTTPProblemReport(
          status = 403,
          title = null,
          detail = "A detail.",
          type = null
        ),
        fallback = "fallback"
      )
    )
  }

  @Test
  fun testProblemReportMessage_BlankTitle_RendersDetailAlone() {
    assertEquals(
      "A detail.",
      AccountAuthenticatedHTTP.problemReportMessage(
        problemReport = LSHTTPProblemReport(
          status = 403,
          title = "   ",
          detail = "A detail.",
          type = null
        ),
        fallback = "fallback"
      )
    )
  }
}
