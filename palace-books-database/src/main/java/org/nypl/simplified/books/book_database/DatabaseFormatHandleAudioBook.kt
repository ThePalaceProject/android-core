package org.nypl.simplified.books.book_database

import android.app.Application
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Preconditions
import net.jcip.annotations.GuardedBy
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerNoOp
import org.librarysimplified.audiobook.api.PlayerBookSource
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.api.ManifestUnparsed
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.bookmark.BookmarkID
import org.nypl.simplified.books.api.bookmark.BookmarkKind
import org.nypl.simplified.books.api.bookmark.SerializedBookmark
import org.nypl.simplified.books.api.bookmark.SerializedBookmarkFallbackValues
import org.nypl.simplified.books.api.bookmark.SerializedBookmarks
import org.nypl.simplified.books.book_database.api.BookDRMInformationHandle
import org.nypl.simplified.books.book_database.api.BookDatabaseEntryFormatHandle.BookDatabaseEntryFormatHandleAudioBook
import org.nypl.simplified.files.DirectoryUtilities
import org.nypl.simplified.files.FileUtilities
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import org.nypl.simplified.json.core.JSONSerializerUtilities
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI

/**
 * Operations on audio book formats in database entries.
 */

internal class DatabaseFormatHandleAudioBook internal constructor(
  private val parameters: DatabaseFormatHandleParameters
) : BookDatabaseEntryFormatHandleAudioBook() {

  private val log =
    LoggerFactory.getLogger(DatabaseFormatHandleAudioBook::class.java)

  private val fileBook: File =
    File(this.parameters.directory, "audiobook-book.zip")
  private val fileManifest: File =
    File(this.parameters.directory, "audiobook-manifest.json")
  private val fileManifestURI: File =
    File(this.parameters.directory, "audiobook-manifest-uri.txt")
  private val fileManifestURITmp: File =
    File(this.parameters.directory, "audiobook-manifest-uri.txt.tmp")
  private val filePosition: File =
    File(this.parameters.directory, "audiobook-position.json")
  private val filePositionTmp: File =
    File(this.parameters.directory, "audiobook-position.json.tmp")
  private val fileBookmarks: File =
    File(this.parameters.directory, "audiobook-bookmarks.json")
  private val fileBookmarksTmp: File =
    File(this.parameters.directory, "audiobook-bookmarks.json.tmp")

  private val dataLock: Any = Any()

  @GuardedBy("dataLock")
  private var drmHandleRef: BookDRMInformationHandle

  init {
    val drmHandleInitial =
      BookDRMInformationHandles.open(
        directory = this.parameters.directory,
        format = this.formatDefinition,
        bookFormats = this.parameters.bookFormatSupport,
        onUpdate = this::onDRMUpdated
      )

    if (drmHandleInitial == null) {
      try {
        FileUtilities.fileDelete(this.fileManifest)
      } catch (e: Exception) {
        // Not much we can do about this.
      }

      val drmHandleNext =
        BookDRMInformationHandles.open(
          directory = this.parameters.directory,
          format = this.formatDefinition,
          bookFormats = this.parameters.bookFormatSupport,
          onUpdate = this::onDRMUpdated
        ) ?: throw IllegalStateException("Still could not open a DRM handle!")

      this.drmHandleRef = drmHandleNext
    } else {
      this.drmHandleRef = drmHandleInitial
    }
  }

  @GuardedBy("dataLock")
  private var formatRef: BookFormat.BookFormatAudioBook =
    synchronized(this.dataLock) {
      loadInitial(
        objectMapper = this.parameters.objectMapper,
        fileBook = this.fileBook,
        fileManifest = this.fileManifest,
        fileManifestURI = this.fileManifestURI,
        fileBookmarks = this.fileBookmarks,
        filePosition = this.filePosition,
        contentType = this.parameters.contentType,
        drmInfo = this.drmInformationHandle.info,
        bookmarkFallbackValues = SerializedBookmarkFallbackValues(
          kind = BookmarkKind.BookmarkExplicit,
          bookOPDSId = this.parameters.entry.book.entry.id,
          bookTitle = this.parameters.entry.book.entry.title
        )
      )
    }

  private fun onDRMUpdated() {
    this.parameters.onUpdated.invoke(this.refreshDRM())
  }

  private fun refreshDRM(): BookFormat.BookFormatAudioBook {
    return synchronized(this.dataLock) {
      this.formatRef = this.formatRef.copy(drmInformation = this.drmInformationHandle.info)
      this.formatRef
    }
  }

  override val format: BookFormat.BookFormatAudioBook
    get() = synchronized(this.dataLock) { this.formatRef }

  override val drmInformationHandle: BookDRMInformationHandle
    get() = synchronized(this.dataLock) { this.drmHandleRef }

  override fun setDRMKind(kind: BookDRMKind) {
    synchronized(this.dataLock) {
      val oldRef = (this.drmHandleRef as BookDRMInformationHandleBase)
      this.drmHandleRef = BookDRMInformationHandles.create(
        directory = this.parameters.directory,
        format = this.formatDefinition,
        drmKind = kind,
        onUpdate = this::onDRMUpdated
      )
      oldRef.close()
      this.onDRMUpdated()
    }
  }

  override fun deleteBookData(context: Application) {
    val briefID = this.parameters.bookID.brief()

    this.log.debug("[{}]: deleting audio book data", briefID)

    /*
     * Parse the manifest, start up an audio engine, and then tell it to delete all and any
     * downloaded parts.
     */

    if (!this.fileManifest.isFile) {
      this.log.debug("[{}]: no manifest available", briefID)
      return
    }

    try {
      FileInputStream(this.fileManifest).use { stream ->
        this.log.debug("[{}]: parsing audio book manifest", briefID)

        val manifestResult: ParseResult<PlayerManifest> =
          ManifestParsers.parse(
            uri = this.fileManifest.toURI(),
            input = ManifestUnparsed(
              palaceId = PlayerPalaceID(this.parameters.entry.book.entry.id),
              data = stream.readBytes()
            )
          )

        when (manifestResult) {
          is ParseResult.Failure -> {
            for (error in manifestResult.errors) {
              this.log.debug(
                "[{}]: parse error: {}:{}: {}",
                briefID,
                error.line,
                error.column,
                error.message
              )
            }
            throw IOException("One or more manifest parse errors occurred")
          }

          is ParseResult.Success -> {
            this.log.debug("[{}]: selecting audio engine", briefID)

            PlayerAudioEngines.delete(
              context = context,
              request = PlayerAudioEngineRequest(
                bookSource = PlayerBookSource.PlayerBookSourceManifestOnly,
                manifest = manifestResult.result,
                filter = { true },
                downloadProvider = NullDownloadProvider(),
                userAgent = PlayerUserAgent("unused"),
                authorizationHandler = PlayerAuthorizationHandlerNoOp,
                bookCredentials = this.drmHandleRef.info.playerCredentials()
              )
            )

            this.log.debug("[{}]: deleted audio book data", briefID)
          }
        }
      }
    } catch (ex: Exception) {
      this.log.error("[{}]: Problem deleting audio book: ", briefID, ex)
    }

    this.parameters.onUpdated.invoke(synchronized(dataLock) {
      FileUtilities.fileDelete(filePosition)

      if (fileBook.isDirectory) {
        DirectoryUtilities.directoryDelete(fileBook)
      } else {
        FileUtilities.fileDelete(fileBook)
      }

      formatRef = formatRef.copy(
        file = null,
        lastReadLocation = null
      )

      formatRef
    })
  }

  override fun deleteBookmark(bookmarkId: BookmarkID) {
    val newFormat = synchronized(this.dataLock) {
      val serialized = this.formatRef.bookmarks.filter { bookmark ->
        bookmark.bookmarkId != bookmarkId
      }

      FileUtilities.fileWriteUTF8Atomically(
        this.fileBookmarks,
        this.fileBookmarksTmp,
        JSONSerializerUtilities.serializeToString(
          serialized.map { x -> x.toJSON(this.parameters.objectMapper) }
        )
      )
      this.formatRef = this.formatRef.copy(bookmarks = serialized)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun copyInManifestAndURI(
    data: ByteArray,
    manifestURI: URI?
  ) {
    val newFormat = synchronized(this.dataLock) {
      FileUtilities.fileWriteBytes(data, this.fileManifest)

      if (manifestURI != null) {
        FileUtilities.fileWriteUTF8Atomically(
          this.fileManifestURI, this.fileManifestURITmp, manifestURI.toString()
        )
      } else {
        FileUtilities.fileDelete(this.fileManifestURI)
      }

      this.formatRef =
        this.formatRef.copy(
          manifest = BookFormat.AudioBookManifestReference(
            manifestURI = manifestURI,
            manifestFile = this.fileManifest
          )
        )
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun setLastReadLocation(bookmark: SerializedBookmark?) {
    if (bookmark != null) {
      Preconditions.checkArgument(
        bookmark.kind == BookmarkKind.BookmarkLastReadLocation,
        "Must use a last-read location bookmark"
      )
    }

    val newFormat = synchronized(this.dataLock) {
      if (bookmark != null) {
        FileUtilities.fileWriteUTF8Atomically(
          this.filePosition,
          this.filePositionTmp,
          JSONSerializerUtilities.serializeToString(bookmark.toJSON(this.parameters.objectMapper))
        )
      } else {
        FileUtilities.fileDelete(this.filePosition)
      }

      this.formatRef = this.formatRef.copy(lastReadLocation = bookmark)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun addBookmark(
    bookmark: SerializedBookmark
  ) {
    Preconditions.checkArgument(
      bookmark.kind == BookmarkKind.BookmarkExplicit,
      "Must use an explicit bookmark"
    )

    val newFormat = synchronized(this.dataLock) {
      val newBookmarks = arrayListOf<SerializedBookmark>()
      newBookmarks.addAll(this.formatRef.bookmarks)
      newBookmarks.removeIf { b -> b.bookmarkId == bookmark.bookmarkId }
      newBookmarks.removeIf { b -> b.kind == BookmarkKind.BookmarkLastReadLocation }
      newBookmarks.add(bookmark)

      FileUtilities.fileWriteUTF8Atomically(
        this.fileBookmarks,
        this.fileBookmarksTmp,
        JSONSerializerUtilities.serializeToString(
          newBookmarks.map { x -> x.toJSON(this.parameters.objectMapper) }
        )
      )
      this.formatRef = this.formatRef.copy(bookmarks = newBookmarks)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  companion object {

    private val logger =
      LoggerFactory.getLogger(DatabaseFormatHandleAudioBook::class.java)

    private fun loadInitial(
      objectMapper: ObjectMapper,
      fileBook: File,
      fileManifest: File,
      fileManifestURI: File,
      fileBookmarks: File,
      filePosition: File,
      contentType: MIMEType,
      drmInfo: BookDRMInformation,
      bookmarkFallbackValues: SerializedBookmarkFallbackValues
    ): BookFormat.BookFormatAudioBook {
      return BookFormat.BookFormatAudioBook(
        file = if (fileBook.exists()) fileBook else null,
        manifest = this.loadManifestIfNecessary(fileManifest, fileManifestURI),
        contentType = contentType,
        drmInformation = drmInfo,
        bookmarks = this.loadBookmarksIfPresent(objectMapper, fileBookmarks, bookmarkFallbackValues),
        lastReadLocation = this.loadLastReadLocationIfPresent(filePosition, bookmarkFallbackValues),
      )
    }

    @Throws(IOException::class)
    private fun loadBookmarksIfPresent(
      objectMapper: ObjectMapper,
      fileBookmarks: File,
      bookmarkFallbackValues: SerializedBookmarkFallbackValues
    ): List<SerializedBookmark> {
      return if (fileBookmarks.isFile) {
        this.loadBookmarks(
          objectMapper = objectMapper,
          fileBookmarks = fileBookmarks,
          bookmarkFallbackValues = bookmarkFallbackValues
        )
      } else {
        listOf()
      }
    }

    private fun loadBookmarks(
      objectMapper: ObjectMapper,
      fileBookmarks: File,
      bookmarkFallbackValues: SerializedBookmarkFallbackValues
    ): List<SerializedBookmark> {
      val tree =
        objectMapper.readTree(fileBookmarks)
      val array =
        JSONParserUtilities.checkArray(null, tree)
      val bookmarks =
        arrayListOf<SerializedBookmark>()

      array.forEach { node ->
        try {
          val fallbackValues =
            bookmarkFallbackValues.copy(kind = BookmarkKind.BookmarkExplicit)
          bookmarks.add(SerializedBookmarks.parseBookmark(node, fallbackValues))
        } catch (exception: JSONParseException) {
          this.logger.debug("Failed to parse bookmark: ", exception)
        }
      }
      return bookmarks
    }

    @Throws(IOException::class)
    private fun loadLastReadLocationIfPresent(
      fileLastRead: File,
      bookmarkFallbackValues: SerializedBookmarkFallbackValues
    ): SerializedBookmark? {
      return if (fileLastRead.isFile) {
        try {
          this.loadLastReadLocation(fileLastRead = fileLastRead, bookmarkFallbackValues)
        } catch (e: Exception) {
          this.logger.debug("Failed to read the last-read location: ", e)
          null
        }
      } else {
        null
      }
    }

    @Throws(IOException::class)
    private fun loadLastReadLocation(
      fileLastRead: File,
      bookmarkFallbackValues: SerializedBookmarkFallbackValues
    ): SerializedBookmark {
      val fallbackValues =
        bookmarkFallbackValues.copy(kind = BookmarkKind.BookmarkLastReadLocation)
      return SerializedBookmarks.parseBookmarkFromString(
        text = FileUtilities.fileReadUTF8(fileLastRead),
        fallbackValues = fallbackValues,
      )
    }

    private fun loadManifestIfNecessary(
      fileManifest: File,
      fileManifestURI: File
    ): BookFormat.AudioBookManifestReference? {
      return if (fileManifest.isFile) {
        this.loadManifest(fileManifest, fileManifestURI)
      } else {
        null
      }
    }

    private fun loadManifest(
      fileManifest: File,
      fileManifestURI: File
    ): BookFormat.AudioBookManifestReference {
      return BookFormat.AudioBookManifestReference(
        manifestFile = fileManifest,
        manifestURI = URI.create(FileUtilities.fileReadUTF8(fileManifestURI))
      )
    }
  }
}
