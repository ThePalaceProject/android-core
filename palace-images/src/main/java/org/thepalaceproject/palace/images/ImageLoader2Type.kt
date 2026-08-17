package org.nypl.simplified.ui.images

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.annotation.DrawableRes
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.feeds.api.FeedEntry
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * An image loader used for image resources.
 */

interface ImageLoader2Type {
  /**
   * Load the account logo into the given image view.
   *
   * @param context The context, for lifecycle control
   * @param account The account
   * @param defaultIcon The default icon to use if the account does not have one
   * @param iconView The target image view
   */

  fun loadAccountLogoIntoView(
    context: Context,
    account: AccountProviderDescription,
    @DrawableRes defaultIcon: Int,
    iconView: ImageView,
  ): CompletableFuture<Unit>

  /**
   * Load the account logo into the given image view.
   *
   * @param account The account
   * @param defaultIcon The default icon to use if the account does not have one
   * @param iconView The target image view
   */

  fun loadAccountLogoIntoView(
    account: AccountProviderDescription,
    @DrawableRes defaultIcon: Int,
    iconView: ImageView,
  ): CompletableFuture<Unit> =
    this.loadAccountLogoIntoView(
      context = iconView.context,
      account = account,
      defaultIcon = defaultIcon,
      iconView = iconView
    )

  /**
   * Load or generate a thumbnail based on `entry` into the image view
   * `imageView`, at width `width` and height `height`.
   *
   * Must only be called from the UI thread.
   *
   * @param entry The feed entry
   * @param imageView The image view
   * @param width Use 0 as desired dimension to resize keeping aspect ratio.
   * @param height Use 0 as desired dimension to resize keeping aspect ratio.
   */

  fun loadThumbnailInto(
    entry: FeedEntry.FeedEntryOPDS,
    imageView: ImageView,
    width: Int,
    height: Int
  ): CompletableFuture<Unit>

  /**
   * Load or generate a cover based on `entry` into the image view
   * `imageView`, at width `width` and height `height`.
   *
   * Must only be called from the UI thread.
   *
   * @param entry The feed entry
   * @param imageView The image view
   * @param hasBadge If the image should have the red icon at the bottom right corner
   * @param width Use 0 as desired dimension to resize keeping aspect ratio.
   * @param height Use 0 as desired dimension to resize keeping aspect ratio.
   */

  fun loadCoverInto(
    entry: FeedEntry.FeedEntryOPDS,
    imageView: ImageView,
    hasBadge: Boolean,
    width: Int,
    height: Int
  ): CompletableFuture<Unit>

  /**
   * Load the cover based on `entry` as bitmap to be used as the argument of the callback
   *
   * @param entry The feed entry
   * @param onBitmapLoaded The callback to call when the image is loaded
   * @param defaultResource The id for the default resource if something goes wrong while loading
   * the bitmap
   *
   * Use 0 as desired dimension to resize keeping aspect ratio.
   */

  fun loadCoverAsBitmap(
    entry: FeedEntry.FeedEntryOPDS,
    onBitmapLoaded: (Bitmap) -> Unit,
    @DrawableRes defaultResource: Int
  ): CompletableFuture<Unit>

  /**
   * Load the cover based on `source` as bitmap to be used as the argument of the callback
   *
   * @param source The feed source
   * @param onBitmapLoaded The callback to call when the image is loaded
   * @param defaultResource The id for the default resource if something goes wrong while loading
   * the bitmap
   *
   * Use 0 as desired dimension to resize keeping aspect ratio.
   */

  fun loadCoverAsBitmap(
    source: URI,
    onBitmapLoaded: (Bitmap) -> Unit,
    @DrawableRes defaultResource: Int
  ): CompletableFuture<Unit>
}
