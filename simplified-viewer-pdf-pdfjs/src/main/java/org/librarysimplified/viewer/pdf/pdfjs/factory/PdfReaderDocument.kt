package org.librarysimplified.viewer.pdf.pdfjs.factory

import android.content.Context
import android.graphics.Bitmap
import com.shockwave.pdfium.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.PdfSupport
import org.readium.r2.shared.util.pdf.PdfDocument
import org.slf4j.LoggerFactory
import com.shockwave.pdfium.PdfDocument as PdfiumDocument

class PdfReaderDocument(
  val core: PdfiumCore,
  val document: PdfiumDocument,
  override val identifier: String?,
  override val pageCount: Int
) : PdfDocument {

  override val title: String?
    get() = metadata.title

  override val author: String?
    get() = metadata.author

  override val subject: String?
    get() = metadata.subject

  override val keywords: List<String>
    get() = metadata.keywords
      .split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }

  private val logger = LoggerFactory.getLogger(PdfReaderDocument::class.java)
  private val metadata: PdfiumDocument.Meta by lazy { core.getDocumentMeta(document) }

  override suspend fun cover(context: Context): Bitmap? {
    return withContext(Dispatchers.IO) {
      try {
        core.openPage(document, 0)
        val width = core.getPageWidth(document, 0)
        val height = core.getPageHeight(document, 0)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        core.renderPageBitmap(document, bitmap, 0, 0, 0, width, height, false)
        bitmap
      } catch (e: Exception) {
        logger.error("Error rendering page: ", e)
        null
      } catch (e: OutOfMemoryError) {
        logger.error("Error rendering page: ", e)
        null
      }
    }
  }

  override val outline: List<PdfDocument.OutlineNode> by lazy {
    core.getTableOfContents(document).map { it.toOutlineNode() }
  }

  override suspend fun close() {
    // do nothing
  }

  @OptIn(PdfSupport::class)
  private fun PdfiumDocument.Bookmark.toOutlineNode(): PdfDocument.OutlineNode {
    return PdfDocument.OutlineNode(
      title = title,
      pageNumber = pageIdx.toInt() + 1,
      children = children.map { it.toOutlineNode() },
    )
  }
}
