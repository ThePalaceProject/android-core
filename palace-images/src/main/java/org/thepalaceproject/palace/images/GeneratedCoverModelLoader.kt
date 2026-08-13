package org.thepalaceproject.palace.images

import android.graphics.Bitmap
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.net.URI

class GeneratedCoverModelLoader(
  private val coverGenerator: BookCoverGeneratorType
) : ModelLoader<URI, Bitmap> {
  override fun handles(uri: URI): Boolean = uri.scheme == "generated-cover"

  override fun buildLoadData(
    uri: URI,
    width: Int,
    height: Int,
    options: com.bumptech.glide.load.Options
  ): ModelLoader.LoadData<Bitmap> =
    ModelLoader.LoadData(
      ObjectKey(uri),
      GeneratedCoverDataFetcher(coverGenerator, uri, width, height)
    )

  class Factory(
    private val coverGenerator: BookCoverGeneratorType
  ) : ModelLoaderFactory<URI, Bitmap> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<URI, Bitmap> = GeneratedCoverModelLoader(coverGenerator)

    override fun teardown() {
      // nothing to clean up
    }
  }
}
