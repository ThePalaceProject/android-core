package org.thepalaceproject.palace.images

import android.graphics.Bitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.net.URI

class GeneratedCoverDataFetcher(
  private val coverGenerator: BookCoverGeneratorType,
  private val uri: URI,
  private val width: Int,
  private val height: Int
) : DataFetcher<Bitmap> {
  override fun loadData(
    priority: Priority,
    callback: DataFetcher.DataCallback<in Bitmap>
  ) {
    val bitmap = coverGenerator.generateImage(uri, width, height)
    callback.onDataReady(bitmap)
  }

  override fun cleanup() {
    // No resources to clean up.
  }

  override fun cancel() {
    // Image generation is synchronous and can't be cancelled.
  }

  override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

  override fun getDataSource(): DataSource = DataSource.LOCAL
}
