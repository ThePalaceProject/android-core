package org.nypl.simplified.tests.books.controller;

import android.app.Application;

import org.mockito.Mockito;

public final class BooksControllerTest extends BooksControllerContract {

  @Override
  protected Application context() {
    return Mockito.mock(Application.class);
  }
}
