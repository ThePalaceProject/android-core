package org.nypl.simplified.tests.books.book_database;

import android.app.Application;

import org.mockito.Mockito;

public final class BookDatabaseTest extends BookDatabaseContract {


  @Override
  protected Application context() {
    return Mockito.mock(Application.class);
  }
}
