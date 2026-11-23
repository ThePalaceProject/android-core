package org.nypl.simplified.audiobook.nowplaying

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.audiobook.nowplaying.api.AudiobookNowPlayingServiceType
import org.nypl.simplified.audiobook.nowplaying.api.AudiobookRef
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.threads.UIThread
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Tracks which audiobook is currently open and can open one for playback.
 *
 * The currently-open book is held as a read-only attribute so that it can be observed. Writes are
 * made on the UI thread so that observers see updates there. The reference is persisted so that it
 * survives process death.
 */

class AudiobookNowPlayingService(
  private val application: Application,
) : AudiobookNowPlayingServiceType {
  private val logger: Logger = LoggerFactory.getLogger(AudiobookNowPlayingService::class.java)
  private val prefs: SharedPreferences =
    application.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

  private val currentAttribute: AttributeType<Optional<AudiobookRef>> =
    Attributes
      .create { e -> logger.debug("Attribute exception: ", e) }
      .withValue(this.loadCurrent())

  override val current: AttributeReadableType<Optional<AudiobookRef>> = currentAttribute

  override fun setCurrent(ref: AudiobookRef?) {
    UIThread.runOnUIThread {
      val newRef = Optional.ofNullable(ref)
      this.saveCurrent(newRef)
      this.currentAttribute.set(newRef)
    }
  }

  override fun openForPlayback(ref: AudiobookRef): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    future.completeExceptionally(
      UnsupportedOperationException("openForPlayback is not implemented"),
    )
    return future
  }

  private fun loadCurrent(): Optional<AudiobookRef> {
    val account = prefs.getString(KEY_ACCOUNT, null)
    if (account == null) {
      return Optional.empty()
    }

    val book = prefs.getString(KEY_BOOK, null)
    if (book == null) {
      return Optional.empty()
    }

    val accountID: AccountID
    val bookID: BookID
    try {
      accountID = AccountID(UUID.fromString(account))
      bookID = BookID.create(book)
    } catch (e: Exception) {
      logger.warn("Could not parse the persisted audiobook reference", e)
      return Optional.empty()
    }

    return Optional.of(AudiobookRef(accountID, bookID))
  }

  private fun saveCurrent(ref: Optional<AudiobookRef>) {
    val editor = prefs.edit()
    if (ref.isPresent) {
      val value = ref.get()
      editor.putString(KEY_ACCOUNT, value.accountID.uuid.toString())
      editor.putString(KEY_BOOK, value.bookID.value())
    } else {
      editor.remove(KEY_ACCOUNT)
      editor.remove(KEY_BOOK)
    }
    editor.apply()
  }

  private companion object {
    private const val PREFS_FILE = "audiobook_now_playing"
    private const val KEY_ACCOUNT = "account_id"
    private const val KEY_BOOK = "book_id"
  }
}
