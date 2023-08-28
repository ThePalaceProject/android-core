package org.nypl.simplified.books.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.SettableFuture
import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Some
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.books.bundled.api.BundledContentResolverType
import org.nypl.simplified.feeds.api.FeedEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.util.concurrent.ExecutorService

/**
 * The default implementation of the book cover provider interface.
 */

class BookCoverProvider private constructor(
  private val bookRegistry: BookRegistryReadableType,
  private val coverGenerator: BookCoverGeneratorType,
  private val picasso: Picasso,
  private val badgeLookup: BookCoverBadgeLookupType
) : BookCoverProviderType {

  private val logger: Logger = LoggerFactory.getLogger(BookCoverProvider::class.java)
  private val coverTag: String = "cover"
  private val thumbnailTag: String = "thumbnail"

  private fun generateCoverURI(entry: FeedEntry.FeedEntryOPDS): URI {
    val feedEntry = entry.feedEntry
    val title = feedEntry.title
    val authors = feedEntry.authors
    val author = authors.firstOrNull() ?: ""
    return this.coverGenerator.generateURIForTitleAuthor(title, author)
  }

  /**
   * @param width Use 0 as desired dimension to resize keeping aspect ratio.
   * @param height Use 0 as desired dimension to resize keeping aspect ratio.
   */
  private fun doLoad(
    entry: FeedEntry.FeedEntryOPDS,
    imageView: ImageView,
    hasBadge: Boolean,
    width: Int,
    height: Int,
    tag: String,
    uriSpecified: URI?
  ): FluentFuture<Unit> {
    val future = SettableFuture.create<Unit>()
    val uriGenerated = this.generateCoverURI(entry)

    val callbackFinal = object : Callback {
      override fun onSuccess() {
        future.set(Unit)
      }

      override fun onError(e: Exception) {
        val ioException =
          IOException(
            StringBuilder(128)
              .append("Failed to load image.\n")
              .append("  URI (specified): ")
              .append(uriSpecified)
              .append('\n')
              .append("  URI (generated): ")
              .append(uriGenerated)
              .append('\n')
              .toString(),
            e
          )

        future.setException(ioException)
      }
    }

    val badgePainter = BookCoverBadgePainter(entry, this.badgeLookup)
    if (uriSpecified != null) {
      this.logger.debug("{}: {}: loading specified uri {}", tag, entry.bookID, uriSpecified)

      val requestCreator = this.picasso.load(uriSpecified.toString())
        .tag(tag)
        .error(R.drawable.cover_error)
        .placeholder(R.drawable.cover_loading)

      if (width > 0 || height > 0) {
        requestCreator.resize(width, height)
      }

      if (hasBadge) {
        requestCreator.transform(badgePainter)
      }

      requestCreator.into(imageView, callbackFinal)
    } else {
      this.logger.debug("{}: {}: loading generated uri {}", tag, entry.bookID, uriGenerated)

      val requestCreator = this.picasso.load(uriGenerated.toString())
        .tag(tag)
        .error(R.drawable.cover_error)
        .placeholder(R.drawable.cover_loading)

      if (width > 0 || height > 0) {
        requestCreator.resize(width, height)
      }

      if (hasBadge) {
        requestCreator.transform(badgePainter)
      }

      requestCreator.into(imageView, callbackFinal)
    }

    return FluentFuture.from(future)
  }

  private fun doLoadCoverAsBitmap(
    coverURI: URI?,
    onBitmapLoaded: (Bitmap) -> Unit,
    defaultResource: Int
  ) {
    this.picasso
      .load(coverURI?.toString())
      .error(defaultResource)
      .into(object : Target {

        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
          onBitmapLoaded(bitmap)
        }

        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
          placeHolderDrawable?.let {
            onBitmapLoaded(getBitmapFromDrawable(it))
          }
        }

        override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
          errorDrawable?.let {
            onBitmapLoaded(getBitmapFromDrawable(it))
          }
        }
      })
  }

  private fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
      if (drawable.bitmap != null) {
        return drawable.bitmap
      }
    }

    val bitmap: Bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
      Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    } else {
      Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
      )
    }

    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
  }

  private fun coverURIOf(entry: FeedEntry.FeedEntryOPDS): URI? {
    val bookWithStatus =
      this.bookRegistry.bookOrNull(entry.bookID)
    return bookWithStatus?.book?.cover?.toURI() ?: mapOptionToNull(entry.feedEntry.cover)
  }

  private fun thumbnailURIOf(entry: FeedEntry.FeedEntryOPDS): URI? {
    val bookWithStatus =
      this.bookRegistry.bookOrNull(entry.bookID)
    return bookWithStatus?.book?.thumbnail?.toURI() ?: mapOptionToNull(entry.feedEntry.thumbnail)
  }

  override fun loadThumbnailInto(
    entry: FeedEntry.FeedEntryOPDS,
    imageView: ImageView,
    width: Int,
    height: Int
  ): FluentFuture<Unit> {
    return doLoad(
      entry = entry,
      imageView = imageView,
      hasBadge = true,
      width = width,
      height = height,
      tag = thumbnailTag,
      uriSpecified = thumbnailURIOf(entry)
    )
  }

  override fun loadCoverInto(
    entry: FeedEntry.FeedEntryOPDS,
    imageView: ImageView,
    hasBadge: Boolean,
    width: Int,
    height: Int
  ): FluentFuture<Unit> {
    return doLoad(
      entry = entry,
      imageView = imageView,
      hasBadge = hasBadge,
      width = width,
      height = height,
      tag = coverTag,
      uriSpecified = coverURIOf(entry)
    )
  }

  override fun loadCoverAsBitmap(
    entry: FeedEntry.FeedEntryOPDS,
    onBitmapLoaded: (Bitmap) -> Unit,
    defaultResource: Int
  ) {
    doLoadCoverAsBitmap(
      coverURI = coverURIOf(entry),
      onBitmapLoaded = onBitmapLoaded,
      defaultResource = defaultResource
    )
  }

  private fun <T> mapOptionToNull(option: OptionType<T>): T? {
    if (option is Some<T>) {
      return option.get()
    } else {
      return null
    }
  }

  override fun loadingThumbnailsPause() {
    this.picasso.pauseTag(this.thumbnailTag)
  }

  override fun loadingThumbnailsContinue() {
    this.picasso.resumeTag(this.thumbnailTag)
  }

  companion object {

    /**
     * Create a new cover provider.
     *
     * @param context The application context
     * @param badgeLookup A function used to look up badge images
     * @param bundledContentResolver A bundled content resolver
     * @param bookRegistry The book registry
     * @param coverGenerator A cover generator
     * @param executor An executor
     *
     * @return A new cover provider
     */

    fun newCoverProvider(
      context: Context,
      bookRegistry: BookRegistryReadableType,
      coverGenerator: BookCoverGeneratorType,
      badgeLookup: BookCoverBadgeLookupType,
      bundledContentResolver: BundledContentResolverType,
      executor: ExecutorService,
      debugCacheIndicators: Boolean,
      debugLogging: Boolean
    ): BookCoverProviderType {
      val picassoBuilder = Picasso.Builder(context)
      picassoBuilder.defaultBitmapConfig(Bitmap.Config.RGB_565)
      picassoBuilder.indicatorsEnabled(debugCacheIndicators)
      picassoBuilder.loggingEnabled(debugLogging)
      picassoBuilder.addRequestHandler(BookCoverGeneratorRequestHandler(coverGenerator))
      picassoBuilder.addRequestHandler(BookCoverBundledRequestHandler(bundledContentResolver))
      picassoBuilder.executor(executor)

      val picasso = picassoBuilder.build()
      return BookCoverProvider(bookRegistry, coverGenerator, picasso, badgeLookup)
    }
  }
}
