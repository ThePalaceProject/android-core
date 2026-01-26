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
}
