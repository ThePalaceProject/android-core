package org.nypl.simplified.books.book_database

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.base.Preconditions
import net.jcip.annotations.GuardedBy
import one.irradia.mime.api.MIMEType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerPositions
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.nypl.simplified.books.api.BookDRMKind
import org.nypl.simplified.books.api.BookDRMInformation
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.api.bookmark.Bookmark
import org.nypl.simplified.books.api.bookmark.BookmarkJSON
import org.nypl.simplified.books.api.bookmark.BookmarkKind
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
import java.lang.IllegalStateException
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
        drmInfo = this.drmInformationHandle.info
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

  override fun deleteBookData() {
    val newFormat = synchronized(this.dataLock) {
      FileUtilities.fileDelete(this.filePosition)

      if (this.fileBook.isDirectory) {
        DirectoryUtilities.directoryDelete(this.fileBook)
      } else {
        FileUtilities.fileDelete(this.fileBook)
      }

      this.formatRef = this.formatRef.copy(
        file = null,
        lastReadLocation = null
      )

      this.formatRef
    }

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
          ManifestParsers.parse(this.fileManifest.toURI(), stream.readBytes())

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

            val engine =
              PlayerAudioEngines.findBestFor(
                PlayerAudioEngineRequest(
                  file = this.fileBook,
                  manifest = manifestResult.result,
                  filter = { true },
                  downloadProvider = NullDownloadProvider(),
                  userAgent = PlayerUserAgent("unused")
                )
              )

            if (engine == null) {
              throw UnsupportedOperationException(
                "No audio engine is available to process the given request"
              )
            }

            this.log.debug(
              "[{}]: selected audio engine: {} {}",
              briefID,
              engine.engineProvider.name(),
              engine.engineProvider.version()
            )

            when (val bookResult = engine.bookProvider.create(this.parameters.context)) {
              is PlayerResult.Success -> bookResult.result.wholeBookDownloadTask.delete()
              is PlayerResult.Failure -> throw bookResult.failure
            }

            this.log.debug("[{}]: deleted audio book data", briefID)
          }
        }
      }
    } catch (ex: Exception) {
      this.log.error("[{}]: failed to delete audio book: ", briefID, ex)
      throw ex
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun copyInManifestAndURI(
    data: ByteArray,
    manifestURI: URI
  ) {
    val newFormat = synchronized(this.dataLock) {
      FileUtilities.fileWriteBytes(
        data, this.fileManifest
      )
      FileUtilities.fileWriteUTF8Atomically(
        this.fileManifestURI, this.fileManifestURITmp, manifestURI.toString()
      )

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

  override fun copyInBook(file: File) {
    val newFormat = synchronized(this.dataLock) {
      if (file.isDirectory) {
        DirectoryUtilities.directoryCopy(file, this.fileBook)
      } else {
        FileUtilities.fileCopy(file, this.fileBook)
      }

      this.formatRef = this.formatRef.copy(
        file = this.fileBook,
        manifest = BookFormat.AudioBookManifestReference(
          manifestURI = URI("manifest.json"),
          manifestFile = this.fileManifest
        )
      )
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun moveInBook(file: File) {
    val newFormat = synchronized(this.dataLock) {
      FileUtilities.fileRename(file, this.fileBook)

      this.formatRef = this.formatRef.copy(
        file = this.fileBook,
        manifest = BookFormat.AudioBookManifestReference(
          manifestURI = URI("manifest.json"),
          manifestFile = this.fileManifest
        )
      )
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun setBookmarks(bookmarks: List<Bookmark.AudiobookBookmark>) {
    val newFormat = synchronized(this.dataLock) {
      FileUtilities.fileWriteUTF8Atomically(
        this.fileBookmarks,
        this.fileBookmarksTmp,
        BookmarkJSON.serializeAudiobookBookmarksToString(this.parameters.objectMapper, bookmarks)
      )
      this.formatRef = this.formatRef.copy(bookmarks = bookmarks)
      this.formatRef
    }

    this.parameters.onUpdated.invoke(newFormat)
  }

  override fun setLastReadLocation(bookmark: Bookmark.AudiobookBookmark?) {

    val newFormat = synchronized(this.dataLock) {
      if (bookmark != null) {
        Preconditions.checkArgument(
          bookmark.kind == BookmarkKind.BookmarkLastReadLocation,
          "Must use a last-read-location bookmark"
        )

        FileUtilities.fileWriteUTF8Atomically(
          this.filePosition,
          this.filePositionTmp,
          JSONSerializerUtilities.serializeToString(
            PlayerPositions.serializeToObjectNode(bookmark.location)
          )
        )
      } else {
        FileUtilities.fileDelete(this.filePosition)
      }

      this.formatRef = this.formatRef.copy(lastReadLocation = bookmark)
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
      drmInfo: BookDRMInformation
    ): BookFormat.BookFormatAudioBook {
      return BookFormat.BookFormatAudioBook(
        file = if (fileBook.exists()) fileBook else null,
        manifest = this.loadManifestIfNecessary(fileManifest, fileManifestURI),
        contentType = contentType,
        drmInformation = drmInfo,
        bookmarks = loadBookmarksIfPresent(objectMapper, fileBookmarks),
        lastReadLocation = loadLastReadLocationIfPresent(objectMapper, filePosition),
      )
    }

    @Throws(IOException::class)
    private fun loadBookmarksIfPresent(
      objectMapper: ObjectMapper,
      fileBookmarks: File
    ): List<Bookmark.AudiobookBookmark> {
      return if (fileBookmarks.isFile) {
        loadBookmarks(
          objectMapper = objectMapper,
          fileBookmarks = fileBookmarks
        )
      } else {
        listOf()
      }
    }

    private fun loadBookmarks(
      objectMapper: ObjectMapper,
      fileBookmarks: File
    ): List<Bookmark.AudiobookBookmark> {
      val tree = objectMapper.readTree(fileBookmarks)
      val array = JSONParserUtilities.checkArray(null, tree)

      val bookmarks = arrayListOf<Bookmark.AudiobookBookmark>()

      array.forEach { node ->
        try {
          val bookmark = BookmarkJSON.deserializeAudiobookBookmarkFromJSON(
            kind = BookmarkKind.BookmarkExplicit,
            node = node
          )

          bookmarks.add(bookmark)
        } catch (exception: JSONParseException) {
          this.logger.debug("Failed to parse an audiobook bookmark from the bookmarks file")
        }
      }

      return bookmarks
    }

    @Throws(IOException::class)
    private fun loadLastReadLocationIfPresent(
      objectMapper: ObjectMapper,
      fileLastRead: File
    ): Bookmark.AudiobookBookmark? {
      return if (fileLastRead.isFile) {
        try {
          loadLastReadLocation(
            objectMapper = objectMapper,
            fileLastRead = fileLastRead
          )
        } catch (e: Exception) {
          logger.error("failed to read the last-read location: ", e)
          null
        }
      } else {
        null
      }
    }

    @Throws(IOException::class)
    private fun loadLastReadLocation(
      objectMapper: ObjectMapper,
      fileLastRead: File
    ): Bookmark.AudiobookBookmark {
      val serialized = FileUtilities.fileReadUTF8(fileLastRead)
      return BookmarkJSON.deserializeAudiobookBookmarkFromString(
        objectMapper = objectMapper,
        kind = BookmarkKind.BookmarkLastReadLocation,
        serialized = serialized
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
