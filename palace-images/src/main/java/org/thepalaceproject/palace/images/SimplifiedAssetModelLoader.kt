package org.thepalaceproject.palace.images

import android.content.Context
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.InputStream
import java.net.URI

class SimplifiedAssetModelLoader(
  private val context: Context
) : ModelLoader<URI, InputStream> {
  override fun handles(uri: URI): Boolean = uri.scheme == "simplified-asset"

  override fun buildLoadData(
    uri: URI,
    width: Int,
    height: Int,
    options: com.bumptech.glide.load.Options
  ): ModelLoader.LoadData<InputStream> =
    ModelLoader.LoadData(
      ObjectKey(uri),
      SimplifiedAssetDataFetcher(context, uri.schemeSpecificPart)
    )

  class Factory(
    private val context: Context
  ) : ModelLoaderFactory<URI, InputStream> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<URI, InputStream> = SimplifiedAssetModelLoader(context)

    override fun teardown() {
      // No resources to clean up.
    }
  }
}
