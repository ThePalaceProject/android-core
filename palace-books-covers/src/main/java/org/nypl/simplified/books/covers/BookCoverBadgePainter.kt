package org.nypl.simplified.books.covers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.squareup.picasso.Transformation
import org.nypl.simplified.feeds.api.FeedEntry

/**
 * An image transformer that optionally adds a badge image to the loaded book cover.
 */

class BookCoverBadgePainter(
  val entry: FeedEntry.FeedEntryOPDS,
  val badges: BookCoverBadgeLookupType
) : Transformation {

  override fun key(): String {
    return "org.nypl.simplified.books.covers.BookCoverBadgePainter"
  }

  override fun transform(source: Bitmap): Bitmap {
    val badge = this.badges.badgeForEntry(this.entry) ?: return source
    val sourceConfig = source.config ?: return source

    val workingBitmap = Bitmap.createBitmap(source)
    val result = workingBitmap.copy(sourceConfig, true)
    val canvas = Canvas(result)

    val margin = 8
    val left = (source.width - badge.width) - margin
    val right = source.width - margin
    val top = (source.height - badge.height) - margin
    val bottom = source.height - margin
    val targetRect = Rect(left, top, right, bottom)

    val backgroundPaint = Paint()
    backgroundPaint.color = Color.parseColor("#43BAE6")
    backgroundPaint.isAntiAlias = true
    canvas.drawCircle(
      targetRect.exactCenterX(),
      targetRect.exactCenterY(),
      targetRect.width() / 2.0f,
      backgroundPaint
    )

    val imagePaint = Paint()
    imagePaint.isAntiAlias = true
    val sourceRect = Rect(0, 0, badge.bitmap.width, badge.bitmap.height)
    canvas.drawBitmap(badge.bitmap, sourceRect, targetRect, imagePaint)

    source.recycle()
    return result
  }
}
