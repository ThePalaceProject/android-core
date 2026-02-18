package org.nypl.simplified.tests.mocking

import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.book_database.api.BookDRMInformationHandle

class MockDRMInformationNoneHandle : BookDRMInformationHandle.NoneHandle() {
  override val info: BookDRMInformation.None
    get() = BookDRMInformation.None
}
