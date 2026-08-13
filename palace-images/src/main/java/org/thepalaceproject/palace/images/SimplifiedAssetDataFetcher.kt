package org.thepalaceproject.palace.images

import android.content.Context
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.InputStream

class SimplifiedAssetDataFetcher(
  private val context: Context,
  private val path: String
) : DataFetcher<InputStream> {
  private var stream: InputStream? = null

  override fun loadData(
    priority: Priority,
    callback: DataFetcher.DataCallback<in InputStream>
  ) {
    try {
      stream = context.assets.open(path)
      callback.onDataReady(stream)
    } catch (e: Exception) {
      callback.onLoadFailed(e)
    }
  }

  override fun cleanup() {
    stream?.close()
    stream = null
  }

  override fun cancel() {
    // Asset reading is synchronous and cannot be cancelled.
  }

  override fun getDataClass(): Class<InputStream> = InputStream::class.java

  override fun getDataSource(): DataSource = DataSource.LOCAL
}
