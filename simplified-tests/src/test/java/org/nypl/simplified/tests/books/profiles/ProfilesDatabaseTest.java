package org.nypl.simplified.tests.books.profiles;

import android.app.Application;

import org.mockito.Mockito;

public final class ProfilesDatabaseTest extends ProfilesDatabaseContract {
  @Override
  protected Application context() {
    return Mockito.mock(Application.class);
  }
}
