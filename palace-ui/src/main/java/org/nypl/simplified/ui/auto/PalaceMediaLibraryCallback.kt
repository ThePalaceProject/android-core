package org.nypl.simplified.ui.auto

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER
import androidx.media3.common.Rating
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FluentFuture
import com.google.common.util.concurrent.ListenableFuture
import com.io7m.jfunctional.Some
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.services.api.ServiceDirectoryType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.futures.FluentFutureExtensions.map
import org.nypl.simplified.ui.main.MainBackgroundBookOpenRequests
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeoutException

class PalaceMediaLibraryCallback : MediaLibraryService.MediaLibrarySession.Callback {

  private val logger =
    LoggerFactory.getLogger(PalaceMediaLibraryCallback::class.java)

  override fun onGetLibraryRoot(
    session: MediaLibraryService.MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    params: MediaLibraryService.LibraryParams?
  ): ListenableFuture<LibraryResult<MediaItem>> {
    this.logger.debug("onGetLibraryRoot")

    val servicesFuture =
      FluentFuture.from(Services.serviceDirectoryFuture())

    return servicesFuture.map { services ->
      this.onGetLibraryRootWithServices(services, params)
    }
  }

  override fun onGetItem(
    session: MediaLibraryService.MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    mediaId: String
  ): ListenableFuture<LibraryResult<MediaItem>> {
    this.logger.debug("onGetItem: {}", mediaId)
    return super.onGetItem(session, browser, mediaId)
  }

  override fun onSubscribe(
    session: MediaLibraryService.MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    parentId: String,
    params: MediaLibraryService.LibraryParams?
  ): ListenableFuture<LibraryResult<Void>> {
    this.logger.debug("onSubscribe: {}", parentId)
    return super.onSubscribe(session, browser, parentId, params)
  }

  override fun onUnsubscribe(
    session: MediaLibraryService.MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    parentId: String
  ): ListenableFuture<LibraryResult<Void>> {
    this.logger.debug("onUnsubscribe: {}", parentId)
    return super.onUnsubscribe(session, browser, parentId)
  }

  override fun onSearch(
    session: MediaLibraryService.MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    query: String,
    params: MediaLibraryService.LibraryParams?
  ): ListenableFuture<LibraryResult<Void>> {
    this.logger.debug("onSearch: {}", query)
    return super.onSearch(session, browser, query, params)
  }

  override fun onGetSearchResult(
    session: MediaLibraryService.MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    query: String,
    page: Int,
    pageSize: Int,
    params: MediaLibraryService.LibraryParams?
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
    this.logger.debug("onGetSearchResult: {} {} {}", query, page, pageSize)
    return super.onGetSearchResult(session, browser, query, page, pageSize, params)
  }

  override fun onConnect(
    session: MediaSession,
    controller: MediaSession.ControllerInfo
  ): MediaSession.ConnectionResult {
    this.logger.debug("onConnect")
    return super.onConnect(session, controller)
  }

  override fun onPostConnect(
    session: MediaSession,
    controller: MediaSession.ControllerInfo
  ) {
    this.logger.debug("onPostConnect")
    super.onPostConnect(session, controller)
  }

  override fun onDisconnected(
    session: MediaSession,
    controller: MediaSession.ControllerInfo
  ) {
    this.logger.debug("onDisconnected")
    super.onDisconnected(session, controller)
  }

  @Deprecated("Deprecated in Java")
  override fun onPlayerCommandRequest(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
    playerCommand: Int
  ): Int {
    this.logger.debug("onPlayerCommandRequest: {}", playerCommand)
    return super.onPlayerCommandRequest(session, controller, playerCommand)
  }

  override fun onSetRating(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
    mediaId: String,
    rating: Rating
  ): ListenableFuture<SessionResult> {
    this.logger.debug("onSetRating: {} {}", mediaId, rating)
    return super.onSetRating(session, controller, mediaId, rating)
  }

  override fun onSetRating(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
    rating: Rating
  ): ListenableFuture<SessionResult> {
    this.logger.debug("onSetRating: {}", rating)
    return super.onSetRating(session, controller, rating)
  }

  override fun onCustomCommand(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
    customCommand: SessionCommand,
    args: Bundle
  ): ListenableFuture<SessionResult> {
    this.logger.debug("onCustomCommand: {}", customCommand)
    return super.onCustomCommand(session, controller, customCommand, args)
  }

  override fun onAddMediaItems(
    mediaSession: MediaSession,
    controller: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>
  ): ListenableFuture<List<MediaItem>> {
    this.logger.debug("onAddMediaItems")
    return super.onAddMediaItems(mediaSession, controller, mediaItems)
  }

  override fun onPlaybackResumption(
    mediaSession: MediaSession,
    controller: MediaSession.ControllerInfo
  ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
    this.logger.debug("onPlaybackResumption")
    return super.onPlaybackResumption(mediaSession, controller)
  }

  override fun onMediaButtonEvent(
    session: MediaSession,
    controllerInfo: MediaSession.ControllerInfo,
    intent: Intent
  ): Boolean {
    this.logger.debug("onMediaButtonEvent")
    return super.onMediaButtonEvent(session, controllerInfo, intent)
  }

  private fun onGetLibraryRootWithServices(
    services: ServiceDirectoryType,
    params: MediaLibraryService.LibraryParams?
  ): LibraryResult<MediaItem> {
    this.logger.debug("onGetLibraryRootWithServices")

    val fakeRootItem =
      MediaMetadata.Builder()
        .setIsPlayable(true)
        .setIsBrowsable(true)
        .setTitle("Audiobooks")
        .build()

    val rootItem =
      MediaItem.Builder()
        .setMediaId("root")
        .setMediaMetadata(fakeRootItem)
        .build()

    return LibraryResult.ofItem(rootItem, params)
  }

  override fun onGetChildren(
    session: MediaLibraryService.MediaLibrarySession,
    browser: MediaSession.ControllerInfo,
    parentId: String,
    page: Int,
    pageSize: Int,
    params: MediaLibraryService.LibraryParams?
  ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
    this.logger.debug("onGetChildren {} (page {}, pageSize {})", parentId, page, pageSize)

    val servicesFuture =
      FluentFuture.from(Services.serviceDirectoryFuture())

    return servicesFuture.map { services ->
      this.onGetChildrenWithServices(services, parentId, page, pageSize, params)
    }
  }

  private fun onGetChildrenWithServices(
    services: ServiceDirectoryType,
    parentId: String,
    page: Int,
    pageSize: Int,
    params: MediaLibraryService.LibraryParams?
  ): LibraryResult<ImmutableList<MediaItem>> {
    this.logger.debug(
      "onGetChildrenWithServices {} (page {}, pageSize {})",
      parentId,
      page,
      pageSize
    )

    val books =
      services.requireService(BookRegistryReadableType::class.java)

    val items =
      mutableListOf<MediaItem>()

    for (bookWithStatus in books.books().values) {
      val book = bookWithStatus.book
      if (bookWithStatus.status !is BookStatus.Loaned.LoanedDownloaded) {
        this.logger.debug(
          "onGetChildrenWithServices: Book '{}' is not downloaded",
          book.entry.title
        )
        continue
      }

      val format = book.findPreferredFormat()
      if (format !is BookFormat.BookFormatAudioBook) {
        this.logger.debug(
          "onGetChildrenWithServices: Book '{}' is not an audiobook ({})",
          book.entry.title,
          format
        )
        continue
      }

      val metadataBuilder =
        MediaMetadata.Builder()
          .setAlbumArtist(book.entry.authorsCommaSeparated)
          .setAlbumTitle(book.entry.title)
          .setArtist(book.entry.authorsCommaSeparated)
          .setIsBrowsable(false)
          .setIsPlayable(true)
          .setTitle(book.entry.title)
          .setWriter(book.entry.authorsCommaSeparated)

      this.setCover(book, metadataBuilder)

      val mediaItem =
        MediaItem.Builder()
          .setMediaId(book.id.value())
          .setMediaMetadata(metadataBuilder.build())
          .build()

      items.add(mediaItem)
    }

    items.sortWith(Comparator { x, y -> this.compareMediaItems(x, y) })
    this.logger.debug("onGetChildrenWithServices: Yielding {} books", items.size)
    return LibraryResult.ofItemList(items, params)
  }

  override fun onSetMediaItems(
    mediaSession: MediaSession,
    controller: MediaSession.ControllerInfo,
    mediaItems: List<MediaItem>,
    startIndex: Int,
    startPositionMs: Long
  ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
    this.logger.debug(
      "onSetMediaItems: startIndex {} startPositionMs: {}",
      startIndex,
      startPositionMs
    )

    for (item in mediaItems) {
      this.logger.debug("onSetMediaItems: item {} {}", item.mediaId, item.mediaMetadata.title)
    }

    val servicesFuture =
      FluentFuture.from(Services.serviceDirectoryFuture())

    return servicesFuture.map({ services ->
      this.onSetMediaItemsWithServices(services, mediaItems, startIndex, startPositionMs)
    }, PalaceAutoThread.executor)
  }

  private fun onSetMediaItemsWithServices(
    services: ServiceDirectoryType,
    mediaItems: List<MediaItem>,
    startIndex: Int,
    startPositionMs: Long
  ): MediaSession.MediaItemsWithStartPosition {
    this.logger.debug("onSetMediaItemsWithServices: starting")

    val bookRegistry =
      services.requireService(BookRegistryReadableType::class.java)
    val mediaItem =
      mediaItems[0]
    val bookWithStatus =
      bookRegistry.bookOrException(BookID.create(mediaItem.mediaId))
    val book =
      bookWithStatus.book
    val format =
      book.findPreferredFormat()
        ?: throw IllegalStateException("Book format for book %s was null".format(book))
    val playerID =
      UUID.randomUUID()

    MainBackgroundBookOpenRequests.requestBookOpen(
      MainBackgroundBookOpenRequests.BookOpenRequest(
        book = book,
        bookFormat = format,
        playerID = playerID
      )
    )

    /*
     * We'll wait 30 seconds for the audiobook to open. We have no choice but to wait here
     * as Android Auto will make a request to the player as soon as this method returns (from
     * the future in which it is running).
     */

    val timeThen = java.time.Instant.now()
    val timeExpire = timeThen.plusSeconds(30L)

    while (true) {
      this.logger.debug(
        "onSetMediaItemsWithServices: Waiting for player instance {} to open...", playerID)

      val timeNow = java.time.Instant.now()
      if (timeNow.isAfter(timeExpire)) {
        throw TimeoutException("Timed out waiting for audiobook to load.")
      }

      val openID = PlayerModel.playerID()
      if (openID == playerID) {
        this.logger.debug("onSetMediaItemsWithServices: Audiobook appears to have opened.")
        break
      }

      try {
        Thread.sleep(1_000L)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
    }

    this.logger.debug("onSetMediaItemsWithServices: finishing")
    return MediaSession.MediaItemsWithStartPosition(
      mediaItems,
      startIndex,
      startPositionMs
    )
  }

  private fun setCover(
    book: Book,
    metadataBuilder: MediaMetadata.Builder
  ) {
    val coverFile = book.cover
    if (coverFile != null) {
      try {
        val data = coverFile.readBytes()
        metadataBuilder.setArtworkData(data, PICTURE_TYPE_FRONT_COVER)
        return
      } catch (e: Throwable) {
        this.logger.debug("Failed to load cover {}: ", coverFile, e)
      }
    }

    try {
      val coverURI = book.entry.cover
      if (coverURI.isSome) {
        metadataBuilder.setArtworkUri((coverURI as Some<URI>).get().toString().toUri())
      }
    } catch (e: Throwable) {
      this.logger.debug("Failed to create cover URI: ", e)
    }
  }

  private fun compareMediaItems(
    x: MediaItem,
    y: MediaItem
  ): Int {
    val xTitle = x.mediaMetadata.title!!.toString()
    val yTitle = y.mediaMetadata.title!!.toString()
    return xTitle.compareTo(yTitle)
  }
}
