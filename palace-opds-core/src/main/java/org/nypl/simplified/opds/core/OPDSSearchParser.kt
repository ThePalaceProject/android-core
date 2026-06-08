package org.nypl.simplified.opds.core

import org.nypl.simplified.opds.core.OPDSXML.getFirstChildElementWithName
import org.nypl.simplified.opds.core.OPDSXML.nodeAsElementWithName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.DOMException
import org.w3c.dom.Document
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * The default implementation of the [OPDSSearchParserType].
 */
class OPDSSearchParser private constructor() : OPDSSearchParserType {

  @Throws(OPDSParseException::class)
  override fun parse(
    uri: URI,
    s: InputStream
  ): OPDSOpenSearch1_1 {
    val timePreParse = System.nanoTime()
    var timePostParse = timePreParse

    try {
      LOG.debug("parsing: {}", uri)

      val d: Document = parseStream(s)
      timePostParse = System.nanoTime()

      val root = d.firstChild
      val eSearch = nodeAsElementWithName(
        root, OPEN_SEARCH_URI, "OpenSearchDescription"
      )

      val eURL = getFirstChildElementWithName(
        eSearch, OPEN_SEARCH_URI, "Url"
      )

      return OPDSOpenSearch1_1(eURL.getAttribute("template"))
    } catch (e: ParserConfigurationException) {
      throw OPDSParseException(e)
    } catch (e: SAXException) {
      throw OPDSParseException(e)
    } catch (e: OPDSParseException) {
      throw e
    } catch (e: IOException) {
      throw OPDSParseException(e)
    } catch (e: DOMException) {
      throw OPDSParseException(e)
    } finally {
      val timeNow = System.nanoTime()
      val timeParse = timePostParse - timePreParse
      val timeInterp = timeNow - timePostParse
      LOG.debug(
        "parsing completed ({}ms - parse: {}ms, interp: {}ms): {}",
        TimeUnit.MILLISECONDS.convert(
          timeParse + timeInterp, TimeUnit.NANOSECONDS
        ),
        TimeUnit.MILLISECONDS.convert(
          timeParse, TimeUnit.NANOSECONDS
        ),
        TimeUnit.MILLISECONDS.convert(
          timeInterp, TimeUnit.NANOSECONDS
        ),
        uri
      )
    }
  }

  companion object {
    @JvmStatic
    private val LOG:
      Logger = LoggerFactory.getLogger(OPDSFeedParser::class.java)

    @JvmStatic
    private val OPEN_SEARCH_URI_TEXT: String =
      "http://a9.com/-/spec/opensearch/1.1/"

    @JvmStatic
    private val OPEN_SEARCH_URI: URI =
      URI.create(OPEN_SEARCH_URI_TEXT)

    /**
     * @return A new search document parser
     */
    fun newParser(): OPDSSearchParserType {
      return OPDSSearchParser()
    }

    @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
    private fun parseStream(
      s: InputStream?
    ): Document {
      val dbf = DocumentBuilderFactory.newInstance()
      dbf.isNamespaceAware = true
      val db = dbf.newDocumentBuilder()
      return db.parse(s)
    }
  }
}
