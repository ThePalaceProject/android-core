package org.thepalaceproject.palace.images

import android.graphics.Bitmap
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.net.URI

class DataURIModelLoader : ModelLoader<URI, Bitmap> {
  override fun handles(uri: URI): Boolean = uri.scheme == "data"

  override fun buildLoadData(
    uri: URI,
    width: Int,
    height: Int,
    options: com.bumptech.glide.load.Options
  ): ModelLoader.LoadData<Bitmap>? {
    val bitmap =
      ImageIconViews.imageFromBase64URI(uri.toString())
        ?: return null
    return ModelLoader.LoadData(
      ObjectKey(uri),
      DataURIDataFetcher(bitmap)
    )
  }

  class Factory : ModelLoaderFactory<URI, Bitmap> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<URI, Bitmap> = DataURIModelLoader()

    override fun teardown() {
      // No resources to clean up.
    }
  }
}
