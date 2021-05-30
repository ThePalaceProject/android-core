package org.nypl.simplified.books.book_database

import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.book_database.api.BookDRMInformationHandle
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.files.FileUtilities
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An information handle for LCP.
 */

class BookDRMInformationHandleLCP(
  private val directory: File,
  format: BookFormats.BookFormatDefinition,
  private val onUpdate: () -> Unit
) : BookDRMInformationHandle.LCPHandle(), BookDRMInformationHandleBase {

  private val closed = AtomicBoolean(false)

  init {
    BookDRMInformationHandles.writeDRMInfo(
      directory = this.directory,
      format = format,
      kind = BookDRMKind.LCP
    )
  }

  private val filePassphrase =
    File(this.directory, "${format.shortName}-passphrase")
  private val filePassphraseTmp =
    File(this.directory, "${format.shortName}-passphrase.tmp")

  private val infoLock: Any = Any()
  private var infoRef: BookDRMInformation.LCP =
    synchronized(this.infoLock) {
      this.loadInitial()
    }

  private fun loadInitial(): BookDRMInformation.LCP {
    return BookDRMInformation.LCP(
      hashedPassphrase = if (this.filePassphrase.isFile) {
        this.filePassphrase.readText().trim()
      } else {
        null
      }
    )
  }

  override val info: BookDRMInformation.LCP
    get() {
      check(!this.closed.get()) { "Handle must not have been closed" }
      return synchronized(this.infoLock) { this.infoRef }
    }

  override fun setHashedPassphrase(passphrase: String): BookDRMInformation.LCP {
    synchronized(this.infoLock) {
      FileUtilities.fileWriteUTF8Atomically(this.filePassphrase, this.filePassphraseTmp, passphrase)
      this.infoRef = this.infoRef.copy(hashedPassphrase = passphrase)
    }

    this.onUpdate.invoke()
    return this.infoRef
  }

  override fun close() {
    this.closed.compareAndSet(false, true)
  }
}
