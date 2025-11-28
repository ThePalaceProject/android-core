package org.nypl.simplified.tests.books.book_database

import android.app.Application
import org.mockito.Mockito

class BookDatabaseAudioBookTest : BookDatabaseAudioBookContract() {
  override fun context(): Application {
    return Mockito.mock(Application::class.java)
  }
}
