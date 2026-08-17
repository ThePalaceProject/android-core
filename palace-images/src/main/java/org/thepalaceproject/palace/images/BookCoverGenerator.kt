package org.thepalaceproject.palace.images

import android.graphics.Bitmap
import org.librarysimplified.http.uri_builder.LSHTTPURIQueryBuilder
import org.librarysimplified.http.uri_builder.LSHTTPURIQueryBuilder.decodeQuery
import org.nypl.simplified.tenprint.TenPrintGeneratorType
import org.nypl.simplified.tenprint.TenPrintInput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.util.Objects
import java.util.SortedMap
import java.util.TreeMap

/**
 * The default implementation of the [BookCoverGeneratorType]
 * interface.
 *
 * This implementation uses the provided [TenPrintGeneratorType] to
 * generate covers when a cover is unavailable or not specified.
 *
 */

class BookCoverGenerator(
  private val generator: TenPrintGeneratorType
) : BookCoverGeneratorType {
  @Throws(IOException::class)
  override fun generateImage(
    uri: URI,
    width: Int,
    height: Int
  ): Bitmap {
    var width = width
    var height = height
    try {
      LOG.debug("generating: {}", uri)

      val params: MutableMap<String, String> =
        getParameters(uri)
      val titleMaybe =
        params.get("title")
      val title =
        Objects.requireNonNullElse(titleMaybe, "")
      val authorMaybe =
        params.get("author")
      val author =
        Objects.requireNonNullElse(authorMaybe, "")

      if (width == 0) {
        width = Math.round(height * .75).toInt()
      }
      if (height == 0) {
        height = Math.round(width / .75).toInt()
      }

      val ib = TenPrintInput.newBuilder()
      ib.setAuthor(author)
      ib.setTitle(title)
      ib.setCoverHeight(height)
      val i = ib.build()
      val cover = this.generator.generate(i)
      return Objects.requireNonNull(cover)
    } catch (e: Throwable) {
      LOG.error("Error generating image for {}: ", uri, e)
      throw IOException(e)
    }
  }

  override fun generateURIForTitleAuthor(
    title: String,
    author: String
  ): URI {
    val params: SortedMap<String, String> = TreeMap()
    params["title"] = title
    params["author"] = author

    return LSHTTPURIQueryBuilder.encodeQuery(
      URI.create("generated-cover://localhost/"),
      params
    )
  }

  companion object {
    private val LOG: Logger =
      LoggerFactory.getLogger(BookCoverGenerator::class.java)

    private fun getParameters(u: URI): MutableMap<String, String> {
      val m: MutableMap<String, String> = HashMap()
      val pairs: List<Pair<String, String>> = decodeQuery(u)
      for (pair in pairs) {
        m[pair.first] = pair.second
      }
      return m
    }
  }
}
