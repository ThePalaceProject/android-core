package org.thepalaceproject.palace.images

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import org.librarysimplified.palace.images.R
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.ui.images.ImageLoader2Type
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.util.concurrent.CompletableFuture

class ImageLoader2 private constructor(
  private val appContext: Context,
  private val bookRegistry: BookRegistryReadableType,
  private val coverGenerator: BookCoverGeneratorType
) : ImageLoader2Type {
  private val logger =
    LoggerFactory.getLogger(ImageLoader2::class.java)
  private val badgeTransform: BookCoverBadgeTransform =
    BookCoverBadgeTransform()

  companion object {
    private val logger =
      LoggerFactory.getLogger(ImageLoader2::class.java)

    fun create(
      context: Application,
      bookRegistry: BookRegistryReadableType,
      coverGenerator: BookCoverGeneratorType
    ): ImageLoader2Type {
      this.logger.debug("Configuring Glide")

      val glide = Glide.get(context)
      val registry = glide.registry

      registry.prepend(
        URI::class.java,
        Bitmap::class.java,
        GeneratedCoverModelLoader.Factory(coverGenerator)
      )

      registry.prepend(
        URI::class.java,
        Bitmap::class.java,
        DataURIModelLoader.Factory()
      )

      registry.prepend(
        URI::class.java,
        InputStream::class.java,
        SimplifiedAssetModelLoader.Factory(context)
      )

      registry.prepend(
        URI::class.java,
        InputStream::class.java,
        HttpURIModelLoader.Factory()
      )

      return ImageLoader2(context.applicationContext, bookRegistry, coverGenerator)
    }
  }

  private fun generateCoverURI(entry: FeedEntry.FeedEntryOPDS): URI {
    val feedEntry = entry.feedEntry
    val title = feedEntry.title
    val authors = feedEntry.authors
    val author = authors.firstOrNull() ?: ""
    return this.coverGenerator.generateURIForTitleAuthor(title, author)
  }

  private fun coverURIOf(entry: FeedEntry.FeedEntryOPDS): URI? {
    val bookWithStatus = this.bookRegistry.bookOrNull(entry.bookID)
    return bookWithStatus?.book?.cover?.toURI() ?: entry.feedEntry.cover
  }

  private fun thumbnailURIOf(entry: FeedEntry.FeedEntryOPDS): URI? {
    val bookWithStatus = this.bookRegistry.bookOrNull(entry.bookID)
    return bookWithStatus?.book?.thumbnail?.toURI() ?: entry.feedEntry.thumbnail
  }

  private class FutureListener(
    private val logger: Logger,
    private val future: CompletableFuture<Unit>
  ) : RequestListener<Drawable> {
    override fun onLoadFailed(
      e: GlideException?,
      model: Any?,
      target: Target<Drawable?>?,
      isFirstResource: Boolean
    ): Boolean {
      this.logger.debug("ImageLoadFailed: ", e)
      this.future.completeExceptionally(e)
      return true
    }

    override fun onResourceReady(
      resource: Drawable?,
      model: Any?,
      target: Target<Drawable?>?,
      dataSource: DataSource?,
      isFirstResource: Boolean
    ): Boolean {
      this.future.complete(Unit)
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
    this.logger.debug("loadAccountLogoIntoView: {}", logo)

    val requestManager = Glide.with(context)
    if (logo != null) {
      requestManager
        .load(logo.hrefURI)
        .fallback(defaultIcon)
        .listener(FutureListener(this.logger, future))
        .into(iconView)
    } else {
      requestManager
        .load(defaultIcon)
        .fallback(defaultIcon)
        .listener(FutureListener(this.logger, future))
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
    val uri = this.thumbnailURIOf(entry) ?: this.generateCoverURI(entry)
    this.logger.debug("loadThumbnailInto: {}", uri)

    val glide = Glide.with(this.appContext)
    var request =
      glide
        .load(uri)
        .error(R.drawable.cover_error)
        .placeholder(R.drawable.cover_loading)
    if (width > 0 || height > 0) {
      request = request.override(width, height)
    }
    request = request.transform(this.badgeTransform)
    request = request.listener(FutureListener(this.logger, future))
    request.into(imageView)
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
    val uri = this.coverURIOf(entry) ?: this.generateCoverURI(entry)
    this.logger.debug("loadCoverInto: {}", uri)

    val glide = Glide.with(this.appContext)
    var request =
      glide
        .load(uri)
        .error(R.drawable.cover_error)
        .placeholder(R.drawable.cover_loading)
    if (width > 0 || height > 0) {
      request = request.override(width, height)
    }
    if (hasBadge) {
      request = request.transform(this.badgeTransform)
    }
    request = request.listener(FutureListener(this.logger, future))
    request.into(imageView)
    return future
  }

  override fun loadCoverAsBitmap(
    entry: FeedEntry.FeedEntryOPDS,
    onBitmapLoaded: (Bitmap) -> Unit,
    defaultResource: Int
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    val uri = this.coverURIOf(entry) ?: this.generateCoverURI(entry)
    this.logger.debug("loadCoverAsBitmap: {}", uri)
    this.loadCoverAsBitmapInternal(uri, onBitmapLoaded, defaultResource, future)
    return future
  }

  override fun loadCoverAsBitmap(
    source: URI,
    onBitmapLoaded: (Bitmap) -> Unit,
    defaultResource: Int
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    this.logger.debug("loadCoverAsBitmap: {}", source)
    this.loadCoverAsBitmapInternal(source, onBitmapLoaded, defaultResource, future)
    return future
  }

  private class RequestReceiver(
    private val onBitmapLoaded: (Bitmap) -> Unit,
    private val future: CompletableFuture<Unit>,
  ) : RequestListener<Bitmap> {
    override fun onLoadFailed(
      e: GlideException?,
      model: Any?,
      target: Target<Bitmap?>?,
      isFirstResource: Boolean
    ): Boolean {
      this.future.completeExceptionally(e)
      return false
    }

    override fun onResourceReady(
      resource: Bitmap?,
      model: Any?,
      target: Target<Bitmap?>?,
      dataSource: DataSource?,
      isFirstResource: Boolean
    ): Boolean {
      resource?.let { this.onBitmapLoaded(it) }
      this.future.complete(Unit)
      return false
    }
  }

  private fun loadCoverAsBitmapInternal(
    uri: URI,
    onBitmapLoaded: (Bitmap) -> Unit,
    defaultResource: Int,
    future: CompletableFuture<Unit>
  ) {
    Glide
      .with(this.appContext)
      .asBitmap()
      .load(uri)
      .error(defaultResource)
      .listener(RequestReceiver(onBitmapLoaded, future))
      .into(BitmapTarget(onBitmapLoaded))
  }

  private class BitmapTarget(
    private val onBitmapLoaded: (Bitmap) -> Unit
  ) : SimpleTarget<Bitmap>() {
    override fun onResourceReady(
      resource: Bitmap,
      transition: Transition<in Bitmap>?
    ) {
      this.onBitmapLoaded(resource)
    }

    override fun onLoadCleared(placeholder: Drawable?) {}
  }
}
