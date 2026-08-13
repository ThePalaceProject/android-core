package org.thepalaceproject.palace.images

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

class BookCoverBadgeTransform : BitmapTransformation() {
  override fun transform(
    pool: BitmapPool,
    source: Bitmap,
    outWidth: Int,
    outHeight: Int
  ): Bitmap = source

  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update("BookCoverBadgeTransform".toByteArray())
  }
}
