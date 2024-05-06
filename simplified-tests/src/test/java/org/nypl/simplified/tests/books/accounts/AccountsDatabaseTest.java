package org.nypl.simplified.tests.books.accounts;

import android.app.Application;

import org.mockito.Mockito;

public final class AccountsDatabaseTest extends AccountsDatabaseContract {

  @Override
  protected Application context() {
    return Mockito.mock(Application.class);
  }
}
