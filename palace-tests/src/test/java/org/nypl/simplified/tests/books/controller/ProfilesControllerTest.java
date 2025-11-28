package org.nypl.simplified.tests.books.controller;

import android.app.Application;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProfilesControllerTest extends ProfilesControllerContract {

  @Override
  protected Application context() {
    return Mockito.mock(Application.class);
  }


  @Override
  protected Logger getLogger() {
    return LoggerFactory.getLogger(ProfilesControllerTest.class);
  }
}
