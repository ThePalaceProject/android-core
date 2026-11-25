package org.thepalaceproject.audiobook.now_playing

import android.app.Application
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import org.librarysimplified.services.api.Services
import org.librarysimplified.viewer.audiobook.AudiobookOpener
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.viewer.spi.ViewerParameters
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.thepalaceproject.audiobook.now_playing.api.AudiobookNowPlayingServiceType
import org.thepalaceproject.audiobook.now_playing.api.AudiobookRef
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Tracks which audiobook is currently open and can open one for playback. The
 * [current] attribute is in memory and reflects the actual playback state; it
 * is empty when no audiobook is open.
 */
class AudiobookNowPlayingService(
  private val application: Application,
) : AudiobookNowPlayingServiceType {
  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  private val currentAttribute: AttributeType<Optional<AudiobookRef>> =
    Attributes
      .create { e ->
        this.logger.debug("now-playing attribute error", e)
      }.withValue(Optional.empty())

  private val stopPlaybackDialogAttribute: AttributeType<Boolean> =
    Attributes
      .create { e ->
        this.logger.debug("stop-playback-dialog attribute error", e)
      }.withValue(false)

  /**
   * @return The audiobook that is currently open, or none if there is no
   *   current audiobook.
   */
  override val current: AttributeReadableType<Optional<AudiobookRef>> =
    this.currentAttribute

  /**
   * @return True while the "stop playback?" confirmation dialog should be open.
   */
  override val stopPlaybackDialogOpen: AttributeReadableType<Boolean> =
    this.stopPlaybackDialogAttribute

  /**
   * @param ref The audiobook to remember as current, or `null` to clear it.
   */
  override fun setCurrent(ref: AudiobookRef?) {
    val newRef: Optional<AudiobookRef>
    if (ref == null) {
      newRef = Optional.empty()
    } else {
      newRef = Optional.of(ref)
    }
    UIThread.runOnUIThread {
      this.currentAttribute.set(newRef)
      if (ref == null) {
        // There is no open book, so there is no dialog to confirm stopping.
        this.stopPlaybackDialogAttribute.set(false)
      }
    }
  }

  /**
   * @param open True if the "stop playback?" confirmation dialog should be
   *   open, false if it should be closed.
   */
  override fun setStopPlaybackDialogOpen(open: Boolean) {
    UIThread.runOnUIThread {
      this.stopPlaybackDialogAttribute.set(open)
    }
  }

  /**
   * @param ref The audiobook to open for playback.
   * @param launchUI Whether to launch the in-app player screen.
   * @return A future that completes when the audiobook is open for playback.
   */
  override fun openForPlayback(
    ref: AudiobookRef,
    launchUI: Boolean,
  ): CompletableFuture<Unit> {
    val registry =
      Services
        .serviceDirectory()
        .requireService(BookRegistryReadableType::class.java)
    val bookWithStatus =
      registry.bookOrNull(ref.bookID)
    if (bookWithStatus == null) {
      val future = CompletableFuture<Unit>()
      future.completeExceptionally(
        NoSuchElementException("No such book: ${ref.bookID}"),
      )
      return future
    }
    val book =
      bookWithStatus.book
    val format =
      book.findFormat(BookFormat.BookFormatAudioBook::class.java)
    if (format == null) {
      val future = CompletableFuture<Unit>()
      future.completeExceptionally(
        IllegalStateException("Book ${ref.bookID} has no audiobook format"),
      )
      return future
    }
    val parameters =
      ViewerParameters(
        viewerID = UUID.randomUUID(),
        flags = emptyMap(),
        onLoginRequested = { accountID ->
          this.logger.debug(
            "Login requested for account {} during playback open; no UI to show.",
            accountID,
          )
        },
      )
    return AudiobookOpener.open(
      context = this.application,
      parameters = parameters,
      book = book,
      format = format,
      launchUI = launchUI,
    )
  }
}
