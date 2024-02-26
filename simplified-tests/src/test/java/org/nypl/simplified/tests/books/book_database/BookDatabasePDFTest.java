package org.nypl.simplified.tests.books.book_database;

import android.app.Application;
import android.content.Context;

import org.mockito.Mockito;

public final class BookDatabasePDFTest extends BookDatabasePDFContract {

  @Override
  protected Application context() {
    return Mockito.mock(Application.class);
  }
}
