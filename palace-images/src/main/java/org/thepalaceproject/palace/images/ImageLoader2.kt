package org.thepalaceproject.palace.images

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.ui.images.ImageLoader2Type
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.CompletableFuture

class ImageLoader2 private constructor() : ImageLoader2Type {

  companion object {
    private val logger =
      LoggerFactory.getLogger(ImageLoader2::class.java)

    fun create(context: Application): ImageLoader2Type {
      this.logger.debug("Configuring Glide")

      val glide = Glide.get(context)
      val registry = glide.registry

      return ImageLoader2()
    }
  }

  private class FutureListener(
    private val future: CompletableFuture<Unit>
  ) : RequestListener<Drawable> {

    override fun onLoadFailed(
      e: GlideException?,
      model: Any?,
      target: Target<Drawable?>?,
      isFirstResource: Boolean
    ): Boolean {
      logger.debug("ImageLoadFailed: ", e)
      future.completeExceptionally(e)
      return true
    }

    override fun onResourceReady(
      resource: Drawable?,
      model: Any?,
      target: Target<Drawable?>?,
      dataSource: DataSource?,
      isFirstResource: Boolean
    ): Boolean {
      future.complete(Unit)
      return true
    }
  }

  override fun loadAccountLogoIntoView(
    context: Context,
    account: AccountProviderDescription,
    defaultIcon: Int,
    iconView: ImageView
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()

    val logo = account.logoURI
    val requestManager = Glide.with(context)
    if (logo != null) {
      requestManager
        .load(logo.hrefURI)
        .fallback(defaultIcon)
        .listener(FutureListener(future))
        .into(iconView)
    } else {
      requestManager
        .load(defaultIcon)
        .fallback(defaultIcon)
        .listener(FutureListener(future))
        .into(iconView)
    }

    return future
  }

  override fun loadThumbnailInto(
    entry: FeedEntry.FeedEntryOPDS,
    imageView: ImageView,
    width: Int,
    height: Int
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    future.completeExceptionally(AssertionError("Unimplemented code!"))
    return future
  }

  override fun loadCoverInto(
    entry: FeedEntry.FeedEntryOPDS,
    imageView: ImageView,
    hasBadge: Boolean,
    width: Int,
    height: Int
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    future.completeExceptionally(AssertionError("Unimplemented code!"))
    return future
  }

  override fun loadCoverAsBitmap(
    entry: FeedEntry.FeedEntryOPDS,
    onBitmapLoaded: (Bitmap) -> Unit,
    defaultResource: Int
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    future.completeExceptionally(AssertionError("Unimplemented code!"))
    return future
  }

  override fun loadCoverAsBitmap(
    source: URI,
    onBitmapLoaded: (Bitmap) -> Unit,
    defaultResource: Int
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    future.completeExceptionally(AssertionError("Unimplemented code!"))
    return future
  }
}
