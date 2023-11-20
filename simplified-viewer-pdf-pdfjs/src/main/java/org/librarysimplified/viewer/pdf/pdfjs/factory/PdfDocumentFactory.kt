package org.librarysimplified.viewer.pdf.pdfjs.factory

import android.content.Context
import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore
import org.readium.r2.shared.PdfSupport
import org.readium.r2.shared.extensions.md5
import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.shared.util.use
import java.io.File
import kotlin.reflect.KClass

@OptIn(PdfSupport::class)
class PdfDocumentFactory(context: Context) : PdfDocumentFactory<PdfReaderDocument> {
  override val documentType: KClass<PdfReaderDocument> = PdfReaderDocument::class

  private val core by lazy { PdfiumCore(context.applicationContext) }

  override suspend fun open(file: File, password: String?): PdfReaderDocument {
    return core.fromFile(file, password)
  }

  override suspend fun open(resource: Resource, password: String?): PdfReaderDocument {
    return resource.openAsFile(password) ?: resource.openBytes(password)
  }

  private suspend fun Resource.openAsFile(password: String?): PdfReaderDocument? {
    return file?.let {
      tryOrNull { open(it, password) }
    }
  }

  private suspend fun Resource.openBytes(password: String?): PdfReaderDocument {
    return use {
      core.fromBytes(read().getOrThrow(), password)
    }
  }

  private fun PdfiumCore.fromFile(file: File, password: String?): PdfReaderDocument {
    return fromDocument(
      newDocument(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), password),
      identifier = file.md5()
    )
  }

  /**
   * Creates a [PdfReaderDocument] from raw bytes.
   */
  private fun PdfiumCore.fromBytes(bytes: ByteArray, password: String?): PdfReaderDocument {
    return fromDocument(
      newDocument(bytes, password),
      identifier = bytes.md5()
    )
  }

  private fun PdfiumCore.fromDocument(
    document: PdfDocument,
    identifier: String?
  ): PdfReaderDocument {
    return PdfReaderDocument(
      core = this,
      document = document,
      identifier = identifier,
      pageCount = getPageCount(document)
    )
  }
}
