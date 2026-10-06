package org.thepalaceproject.palace.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * A Glide bitmap transformation that overlays small, semi-transparent text
 * chips (the book format and the DRM system) on the bottom-left corner of the
 * source bitmap. The chips are sized relative to the bitmap so they scale with
 * the cover. If no labels are provided, it passes the source through.
 *
 * This is a debug-only aid: the lookup is expected to return `null` in release
 * builds, in which case this transform is a no-op.
 */

class BookCoverDebugLabelsTransform(
  private val labels: BookCoverDebugLabels?
) : BitmapTransformation() {
  override fun transform(
    pool: BitmapPool,
    source: Bitmap,
    outWidth: Int,
    outHeight: Int
  ): Bitmap {
    if (this.labels == null) {
      return source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    }

    val result = source.copy(source.config ?: Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)

    // Size everything relative to the source height so the labels scale with
    // the cover and we do not need access to display density here.
    val textSize = (source.height * 0.07f).coerceAtLeast(10f)
    val padding = textSize * 0.6f

    val textPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.WHITE
        this.textSize = textSize
        this.typeface = Typeface.DEFAULT_BOLD
      }
    val backgroundPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = Color.argb(170, 0, 0, 0)
      }

    val rowGap = source.height * 0.02f
    val bottom =
      drawChip(
        canvas = canvas,
        backgroundPaint = backgroundPaint,
        textPaint = textPaint,
        sourceWidth = source.width,
        sourceHeight = source.height,
        text = this.labels.format,
        padding = padding,
        rowFromBottom = 0
      )
    drawChip(
      canvas = canvas,
      backgroundPaint = backgroundPaint,
      textPaint = textPaint,
      sourceWidth = source.width,
      sourceHeight = source.height,
      text = this.labels.drm,
      padding = padding,
      rowFromBottom = 1,
      aboveBottom = bottom
    )

    return result
  }

  private fun drawChip(
    canvas: Canvas,
    backgroundPaint: Paint,
    textPaint: Paint,
    sourceWidth: Int,
    sourceHeight: Int,
    text: String,
    padding: Float,
    rowFromBottom: Int,
    aboveBottom: Float? = null
  ): Float {
    val rowGap = sourceHeight * 0.02f
    val margin = sourceHeight * 0.02f
    val upper = text.uppercase()
    val chipHeight = textPaint.textSize * 1.9f
    val chipWidth = padding * 2 + textPaint.measureText(upper)

    // Bottom-left placement so the chips do not collide with the bottom-right
    // audiobook badge drawn by [BookCoverBadgeTransform].
    val left = margin
    val bottom =
      if (rowFromBottom == 0) {
        sourceHeight - margin
      } else {
        (aboveBottom ?: sourceHeight - margin) - rowGap - chipHeight
      }
    val top = bottom - chipHeight

    val rect = RectF(left, top, left + chipWidth, bottom)
    val radius = rect.height() / 3.0f
    canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
    canvas.drawText(upper, rect.left + padding, rect.bottom - padding, textPaint)

    return bottom
  }

  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    // The label text must be part of the cache key, otherwise covers with
    // different labels could share a transformed bitmap.
    val key =
      if (this.labels != null) {
        "BookCoverDebugLabelsTransform:${this.labels.format}|${this.labels.drm}"
      } else {
        "BookCoverDebugLabelsTransform:none"
      }
    messageDigest.update(key.toByteArray())
  }
}
