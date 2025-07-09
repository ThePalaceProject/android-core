package org.nypl.simplified.books.book_database

import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.book_database.api.BookDRMInformationHandle
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.files.FileUtilities
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An information handle for Boundless.
 */

class BookDRMInformationHandleBoundless(
  private val directory: File,
  format: BookFormats.BookFormatDefinition,
  private val onUpdate: () -> Unit
) : BookDRMInformationHandle.BoundlessHandle(), BookDRMInformationHandleBase {

  private val closed = AtomicBoolean(false)

  companion object {
    fun nameLicense(format: BookFormats.BookFormatDefinition) =
      "${format.shortName}-license_boundless.json"
  }

  init {
    BookDRMInformationHandles.writeDRMInfo(
      directory = directory,
      format = format,
      kind = BookDRMKind.BOUNDLESS
    )
  }

  private val fileLicense =
    File(this.directory, nameLicense(format))

  private val infoLock: Any = Any()
  private var infoRef: BookDRMInformation.Boundless =
    synchronized(this.infoLock) {
      this.loadInitial(fileLicense)
    }

  private fun loadInitial(
    license: File
  ): BookDRMInformation.Boundless {
    return BookDRMInformation.Boundless(
      license = license.takeIf { it.isFile },
    )
  }

  override val info: BookDRMInformation.Boundless
    get() {
      check(!this.closed.get()) { "Handle must not have been closed" }
      return synchronized(this.infoLock) { this.infoRef }
    }

  override fun copyInBoundlessLicense(file: File): BookDRMInformation.Boundless {
    synchronized(this.infoLock) {
      FileUtilities.fileCopy(file, this.fileLicense)
      this.infoRef = this.infoRef.copy(license = this.fileLicense)
    }

    this.onUpdate.invoke()
    return this.infoRef
  }

  override fun close() {
    this.closed.compareAndSet(false, true)
  }
}
