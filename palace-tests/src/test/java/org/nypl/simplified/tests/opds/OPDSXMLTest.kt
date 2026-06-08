package org.nypl.simplified.tests.opds

import com.io7m.jnull.NullCheck
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.nypl.simplified.opds.core.OPDSXML.getFirstChildElementWithName
import org.nypl.simplified.opds.core.OPDSXML.getNodeNamespace
import org.nypl.simplified.opds.core.OPDSXML.nodeHasName
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class OPDSXMLTest {
  @Test
  @Throws(Exception::class)
  fun testNamespaces_0() {
    val d: Document =
      parseStream(getResource("namespaces-0.xml"))
    val r = d.getDocumentElement()
    Assertions.assertEquals("feed", r.getNodeName())

    val p = r.getElementsByTagName("dcterms:publisher")
    Assertions.assertEquals(1, p.getLength())
    val pub_actual = p.item(0) as Element

    val pub_ns = getNodeNamespace(pub_actual)
    Assertions.assertEquals(pub_ns, "http://purl.org/dc/terms/")

    Assertions.assertTrue(
      nodeHasName(
        pub_actual,
        URI.create("http://purl.org/dc/terms/"),
        "publisher"
      )
    )

    getFirstChildElementWithName(
      r,
      URI.create("http://purl.org/dc/terms/"),
      "publisher"
    )
  }

  companion object {
    @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
    private fun parseStream(
      s: InputStream?
    ): Document {
      val dbf = DocumentBuilderFactory.newInstance()
      dbf.setNamespaceAware(true)
      val db = dbf.newDocumentBuilder()
      val d = NullCheck.notNull<Document>(db.parse(s))
      return d
    }

    @Throws(Exception::class)
    private fun getResource(
      name: String
    ): InputStream? {
      val path = "/org/nypl/simplified/tests/opds/" + name
      val url = OPDSFeedEntryParserTest::class.java.getResource(path)
      if (url == null) {
        throw FileNotFoundException(path)
      }
      return url.openStream()
    }
  }
}
