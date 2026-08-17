package org.thepalaceproject.palace.images

import android.graphics.Bitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

class DataURIDataFetcher(
  private val bitmap: Bitmap
) : DataFetcher<Bitmap> {
  override fun loadData(
    priority: Priority,
    callback: DataFetcher.DataCallback<in Bitmap>
  ) {
    callback.onDataReady(bitmap)
  }

  override fun cleanup() {
    // No resources to clean up.
  }

  override fun cancel() {
    // Decoding is synchronous and cannot be cancelled.
  }

  override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

  override fun getDataSource(): DataSource = DataSource.LOCAL
}
