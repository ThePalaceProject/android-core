package org.thepalaceproject.audiobook.now_playing

import android.app.Application
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import org.librarysimplified.services.api.Services
import org.librarysimplified.viewer.audiobook.AudiobookOpener
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
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
 * Remembers the audiobook that was most recently opened, persisting it in the
 * current profile's preferences. The [current] attribute mirrors the stored
 * value so that it can be observed.
 */
class AudiobookNowPlayingService(
  private val application: Application,
  private val profiles: ProfilesControllerType,
) : AudiobookNowPlayingServiceType {
  private val logger: Logger = LoggerFactory.getLogger(this::class.java)

  private val currentAttribute: AttributeType<Optional<AudiobookRef>> =
    Attributes
      .create { e ->
        this.logger.debug("now-playing attribute error", e)
      }.withValue(this.loadCurrent())

  /**
   * @return The audiobook that was most recently opened, or none if there is
   *   no current audiobook.
   */
  override val current: AttributeReadableType<Optional<AudiobookRef>> =
    this.currentAttribute

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
      this.saveCurrent(newRef)
      this.currentAttribute.set(newRef)
    }
  }

  /**
   * @param ref The audiobook to open for playback.
   * @return A future that completes when the audiobook is open for playback.
   */
  override fun openForPlayback(ref: AudiobookRef): CompletableFuture<Unit> {
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
      launchUI = false,
    )
  }

  private fun loadCurrent(): Optional<AudiobookRef> {
    val ref =
      this.profiles
        .profileCurrent()
        .preferences()
        .lastAudiobook
    if (ref == null) {
      return Optional.empty()
    }
    return Optional.of(ref)
  }

  private fun saveCurrent(ref: Optional<AudiobookRef>) {
    this.profiles.profileUpdate { d ->
      d.copy(
        preferences =
          d.preferences.copy(
            lastAudiobook = ref.orElse(null),
          ),
      )
    }
  }
}
