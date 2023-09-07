package org.librarysimplified.viewer.pdf.pdfjs

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status
import org.nanohttpd.router.RouterNanoHTTPD
import org.nypl.simplified.books.api.BookDRMInformation
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.publication.ContentProtection
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.protectionError
import org.readium.r2.shared.util.getOrDefault
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.logging.ConsoleWarningLogger
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.Streamer
import org.readium.r2.streamer.parser.pdf.PdfParser
import org.readium.r2.streamer.parser.readium.ReadiumWebPubParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class PdfServer private constructor(
  val port: Int,
  private val context: Context,
  private val publication: Publication
) : RouterNanoHTTPD("127.0.0.1", port) {

  companion object {

    suspend fun create(
      contentProtections: List<ContentProtection>,
      context: Context,
      drmInfo: BookDRMInformation,
      pdfFile: File,
      port: Int
    ): PdfServer {
      val streamer = Streamer(
        context = context,
        parsers = listOf(
          ReadiumWebPubParser(
            httpClient = DefaultHttpClient(),
            pdfFactory = null
          ),
          PdfParser(
            context = context,
          )
        ),
        contentProtections = contentProtections,
        ignoreDefaultParsers = true
      )

      val publication = streamer.open(
        asset = FileAsset(
          file = pdfFile,
          mediaType = when (drmInfo) {
            is BookDRMInformation.LCP -> MediaType.LCP_PROTECTED_PDF
            else -> MediaType.PDF
          }
        ),
        allowUserInteraction = false,
        warnings = ConsoleWarningLogger()
      )
        .getOrElse {
          throw IOException("Failed to open PDF", it)
        }

      return PdfServer(
        port = port,
        context = context,
        publication = publication
      )
    }
  }

  private var pdfResource: Resource? = null

  init {
    if (publication.isRestricted) {
      throw IOException("Failed to unlock PDF", publication.protectionError)
    }

    // We only support a single PDF file in the archive.
    val link = publication.readingOrder.first()
    val pdfResource = publication.get(link)

    addRoute("/assets/(.*)", AssetHandler::class.java, context)
    addRoute("/book.pdf", PdfHandler::class.java, pdfResource)

    this.pdfResource = pdfResource
  }

  override fun stop() {
    super.stop()

    runBlocking {
      this@PdfServer.pdfResource?.close()
    }

    this.publication.close()
  }

  class AssetHandler : BaseHandler() {
    override fun handle(
      resource: UriResource,
      uri: Uri,
      parameters: Map<String, String>?,
      session: IHTTPSession
    ): Response {
      val filename = uri.pathSegments.drop(1).joinToString("/")
      val context = resource.initParameter(Context::class.java)
      val assetStream = context.assets.open(filename)

      val mediaType = runBlocking {
        MediaType.of(fileExtension = File(filename).extension) ?: MediaType.BINARY
      }

      return Response.newChunkedResponse(
        Status.OK,
        mediaType.toString(),
        assetStream
      )
    }
  }

  class PdfHandler : BaseHandler() {
    private var length: Long? = null

    override fun handle(
      resource: UriResource,
      uri: Uri,
      parameters: Map<String, String>?,
      session: IHTTPSession
    ): Response {
      val pdfResource = resource.initParameter(Resource::class.java)

      val length = this.length ?: runBlocking {
        pdfResource.length()
      }
        .getOrDefault(0L)
        .also {
          this.length = it
        }

      val range = session.headers["range"]

      return if (range == null) {
        handleFull(length)
      } else {
        handlePartial(pdfResource, length, range)
      }
    }

    private fun handleFull(
      length: Long
    ): Response {
      return Response.newChunkedResponse(
        Status.OK,
        "application/pdf",
        // This initial response will be discarded by the PDF viewer once it sees that range
        // requests are supported, so we can just return some dummy data.
        ByteArrayInputStream(ByteArray(8))
      ).apply {
        addHeader("Accept-Ranges", "bytes")
        addHeader("Cache-Control", "no-store")
        addHeader("Content-Length", "$length")
      }
    }

    private fun handlePartial(
      pdfResource: Resource,
      length: Long,
      range: String
    ): Response {
      val longRange = parseRange(range)

      val data = runBlocking {
        pdfResource.read(longRange)
      }.getOrDefault(ByteArray(0))

      val start = longRange.first
      val end = longRange.last

      return Response.newFixedLengthResponse(
        Status.PARTIAL_CONTENT,
        "application/pdf",
        data
      ).apply {
        addHeader("Accept-Ranges", "bytes")
        addHeader("Cache-Control", "no-store")
        addHeader("Content-Range", "bytes $start-$end/$length")
      }
    }

    private fun parseRange(range: String): LongRange {
      val (start, end) = range.trim().substringAfter("bytes=").split("-").map {
        it.toLong()
      }

      return LongRange(start, end)
    }
  }

  abstract class BaseHandler : DefaultHandler() {
    private val log: Logger = LoggerFactory.getLogger(BaseHandler::class.java)

    private fun createErrorResponse(status: Status) =
      Response.newFixedLengthResponse(status, "text/html", "")

    val notFoundResponse: Response
      get() = createErrorResponse(Status.NOT_FOUND)

    override fun getMimeType() = null
    override fun getText() = ""
    override fun getStatus() = Status.OK

    override fun get(
      uriResource: UriResource?,
      urlParams: Map<String, String>?,
      session: IHTTPSession?
    ): Response {
      uriResource ?: return notFoundResponse
      session ?: return notFoundResponse

      log.debug("{} {}", session.method, session.uri)

      return try {
        val uri = Uri.parse(session.uri)

        handle(
          resource = uriResource,
          uri = uri,
          parameters = urlParams,
          session = session
        )
      } catch (e: FileNotFoundException) {
        log.debug("File not found in pdf server handler: {}", e.toString())

        notFoundResponse
      } catch (e: Exception) {
        log.debug("Error in pdf server handler: {}", e.toString())

        createErrorResponse(Status.INTERNAL_ERROR)
      }
    }

    abstract fun handle(
      resource: UriResource,
      uri: Uri,
      parameters: Map<String, String>?,
      session: IHTTPSession
    ): Response
  }
}
