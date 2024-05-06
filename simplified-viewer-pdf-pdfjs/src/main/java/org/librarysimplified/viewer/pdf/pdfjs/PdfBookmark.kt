package org.librarysimplified.viewer.pdf.pdfjs

import org.joda.time.DateTime

data class PdfBookmark(
  val kind: PdfBookmarkKind,
  val pageNumber: Int,
  val time: DateTime,
)
