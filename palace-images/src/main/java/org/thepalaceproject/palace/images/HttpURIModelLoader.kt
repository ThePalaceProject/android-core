package org.thepalaceproject.palace.images

import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.HttpGlideUrlLoader
import com.bumptech.glide.signature.ObjectKey
import java.io.InputStream
import java.net.URI

class HttpURIModelLoader : ModelLoader<URI, InputStream> {
  override fun handles(uri: URI): Boolean = uri.scheme == "http" || uri.scheme == "https"

  override fun buildLoadData(
    uri: URI,
    width: Int,
    height: Int,
    options: com.bumptech.glide.load.Options
  ): ModelLoader.LoadData<InputStream> {
    val glideUrl = GlideUrl(uri.toURL())
    val timeout = options.get(HttpGlideUrlLoader.TIMEOUT) ?: 2500
    return ModelLoader.LoadData(
      ObjectKey(uri),
      com.bumptech.glide.load.data
        .HttpUrlFetcher(glideUrl, timeout)
    )
  }

  class Factory : ModelLoaderFactory<URI, InputStream> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<URI, InputStream> = HttpURIModelLoader()

    override fun teardown() {
      // No resources to clean up.
    }
  }
}
