package org.librarysimplified.viewer.pdf.pdfjs

import android.app.Application
import com.shockwave.pdfium.PdfiumCore
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.extensions.md5
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.ReadTry
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.shared.util.resource.Resource
import kotlin.reflect.KClass

class PdfDocumentFactory(
  context: Application
) : PdfDocumentFactory<PdfReaderDocument> {

  private val core =
    PdfiumCore(context)

  override val documentType: KClass<PdfReaderDocument> =
    PdfReaderDocument::class

  @OptIn(InternalReadiumApi::class)
  override suspend fun open(
    resource: Resource,
    password: String?
  ): ReadTry<PdfReaderDocument> {
    return when (val r = resource.read()) {
      is Try.Failure -> Try.failure(r.value)
      is Try.Success -> {
        val document = core.newDocument(r.value, password)
        return Try.success(
          PdfReaderDocument(
            core = this.core,
            document = document,
            identifier = r.value.md5(),
            pageCount = core.getPageCount(document)
          )
        )
      }
    }
  }
}
