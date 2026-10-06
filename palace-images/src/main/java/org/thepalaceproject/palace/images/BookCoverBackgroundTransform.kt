package org.thepalaceproject.palace.images

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * Reduces the source cover to a small bilinear-filtered bitmap so that, stretched
 * to fill its view, it reads as a blurred average of the cover. Applies no badge
 * or other decoration.
 */

class BookCoverBackgroundTransform : BitmapTransformation() {
  override fun transform(
    pool: BitmapPool,
    source: Bitmap,
    outWidth: Int,
    outHeight: Int
  ): Bitmap = Bitmap.createScaledBitmap(source, SIZE, SIZE, true)

  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update("BookCoverBackgroundTransform:${SIZE}x$SIZE".toByteArray())
  }

  companion object {
    private const val SIZE = 8
  }
}
