package org.thepalaceproject.palace.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * A Glide bitmap transformation that overlays a badge icon on the bottom-right
 * corner of the source bitmap. If no badge is provided, it passes the source through.
 */

class BookCoverBadgeTransform(
  private val badge: BookCoverBadge?
) : BitmapTransformation() {
  override fun transform(
    pool: BitmapPool,
    source: Bitmap,
    outWidth: Int,
    outHeight: Int
  ): Bitmap {
    if (this.badge == null) {
      return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    }

    val result = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)

    val margin = 8
    val left = source.width - this.badge.width - margin
    val right = source.width - margin
    val top = source.height - this.badge.height - margin
    val bottom = source.height - margin
    val targetRect = Rect(left, top, right, bottom)

    // Blue circular background behind the badge icon
    val backgroundPaint = Paint()
    backgroundPaint.color = Color.parseColor("#43BAE6")
    backgroundPaint.isAntiAlias = true
    canvas.drawCircle(
      targetRect.exactCenterX(),
      targetRect.exactCenterY(),
      targetRect.width() / 2.0f,
      backgroundPaint
    )

    // Badge icon drawn over the circle
    val imagePaint = Paint()
    imagePaint.isAntiAlias = true
    val sourceRect =
      Rect(0, 0, this.badge.bitmap.width, this.badge.bitmap.height)
    canvas.drawBitmap(this.badge.bitmap, sourceRect, targetRect, imagePaint)

    return result
  }

  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    val key =
      if (this.badge != null) {
        "BookCoverBadgeTransform:${this.badge.width}x${this.badge.height}"
      } else {
        "BookCoverBadgeTransform:none"
      }
    messageDigest.update(key.toByteArray())
  }
}
