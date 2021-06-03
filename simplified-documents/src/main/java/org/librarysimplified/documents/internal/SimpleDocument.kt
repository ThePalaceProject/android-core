package org.librarysimplified.documents.internal

import org.librarysimplified.documents.DocumentType
import java.net.URL

class SimpleDocument(override val readableURL: URL) : DocumentType {
  override fun update() {
  }
}
