package org.nypl.simplified.audiobook.nowplaying.api

import java.util.concurrent.CompletableFuture

/**
 * The service that tracks which audiobook is currently open and can open one for playback.
 *
 * The recorded reference is persisted so that it survives process death.
 */

interface AudiobookNowPlayingServiceType {
  /**
   * @return The audiobook that is currently open, or null if none is open
   */
  val current: AudiobookRef?

  /**
   * Set (or clear) the currently-open audiobook.
   *
   * @param ref The audiobook that is now open, or null to clear
   */
  fun setCurrent(ref: AudiobookRef?)

  /**
   * Open the given audiobook for playback without showing any user interface.
   *
   * If the book is already open this is a no-op that completes immediately; otherwise the book is
   * opened fresh.
   *
   * @param ref The audiobook to open
   * @return A future that completes when the book is open (or already was)
   */
  fun openForPlayback(ref: AudiobookRef): CompletableFuture<Unit>
}
