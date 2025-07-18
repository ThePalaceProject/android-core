package org.nypl.simplified.books.book_database

import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.book_database.api.BookDRMInformationHandle
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.files.DirectoryUtilities
import org.nypl.simplified.files.FileUtilities
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException

/**
 * Functions to open DRM information handle.
 */

object BookDRMInformationHandles {

  private val logger =
    LoggerFactory.getLogger(BookDRMInformationHandles::class.java)

  /**
   * Open a DRM information handle for the given format, from the given directory. If no DRM
   * info file is present, try to infer the DRM system from the files present.
   */

  fun open(
    directory: File,
    format: BookFormats.BookFormatDefinition,
    bookFormats: BookFormatSupportType,
    onUpdate: () -> Unit
  ): BookDRMInformationHandle? {
    val drmInfoFile =
      File(directory, "${format.shortName}-drm.txt")

    val drmKind =
      try {
        BookDRMKind.valueOf(drmInfoFile.readText().trim())
      } catch (e: FileNotFoundException) {
        this.inferDRMKind(directory, format)
      } catch (e: Exception) {
        this.logger.error("could not read {}: ", drmInfoFile, e)
        this.inferDRMKind(directory, format)
      }

    /*
     * Create an initial DRM handle, and then use it to delete the "unsupported" data.
     */

    val createInitial = this.create(directory, format, drmKind) { }
    return if (bookFormats.isDRMSupported(drmKind)) {
      this.create(directory, format, drmKind, onUpdate)
    } else {
      try {
        FileUtilities.fileDelete(drmInfoFile)
      } catch (e: Exception) {
        this.logger.debug("unable to delete DRM file: ", e)
      }

      when (createInitial) {
        is BookDRMInformationHandle.ACSHandle -> {
          createInitial.setACSMFile(null)
          createInitial.setAdobeRightsInformation(null)
          null
        }
        is BookDRMInformationHandle.BoundlessHandle -> null
        is BookDRMInformationHandle.LCPHandle -> null
        is BookDRMInformationHandle.NoneHandle -> null
      }
    }
  }

  private fun inferDRMKind(
    directory: File,
    format: BookFormats.BookFormatDefinition
  ): BookDRMKind {
    val acsNames = BookDRMInformationHandleACS.names(format)
    if (acsNames.any { File(directory, it).exists() }) {
      this.logger.debug("one of {} exists, inferring ACS DRM", acsNames)
      return BookDRMKind.ACS
    }

    this.logger.debug("inferring no DRM")
    return BookDRMKind.NONE
  }

  /**
   * Open a DRM information handle for the given format, from the given directory. If no DRM
   * info file is present, try to infer the DRM system from the files present.
   */

  fun create(
    directory: File,
    format: BookFormats.BookFormatDefinition,
    drmKind: BookDRMKind,
    onUpdate: () -> Unit
  ): BookDRMInformationHandle {
    return when (drmKind) {
      BookDRMKind.NONE ->
        BookDRMInformationHandleNone(directory, format)
      BookDRMKind.LCP ->
        BookDRMInformationHandleLCP(directory, format, onUpdate)
      BookDRMKind.ACS ->
        BookDRMInformationHandleACS(directory, format, onUpdate)
      BookDRMKind.BOUNDLESS ->
        BookDRMInformationHandleBoundless(directory, format, onUpdate)
    }
  }

  /**
   * Write the name of the given DRM system for to the DRM info file for the given format.
   */

  fun writeDRMInfo(
    directory: File,
    format: BookFormats.BookFormatDefinition,
    kind: BookDRMKind
  ) {
    DirectoryUtilities.directoryCreate(directory)

    val drmInfoFile =
      File(directory, "${format.shortName}-drm.txt")
    val drmInfoFileTmp =
      File(directory, "${format.shortName}-drm.txt.tmp")

    FileUtilities.fileWriteUTF8Atomically(drmInfoFile, drmInfoFileTmp, kind.name)
  }
}
