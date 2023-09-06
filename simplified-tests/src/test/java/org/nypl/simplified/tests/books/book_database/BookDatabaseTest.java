package org.nypl.simplified.tests.books.book_database;

import android.content.Context;

import org.mockito.Mockito;

public final class BookDatabaseTest extends BookDatabaseContract {


  @Override
  protected Context context() {
    return Mockito.mock(Context.class);
  }
}
