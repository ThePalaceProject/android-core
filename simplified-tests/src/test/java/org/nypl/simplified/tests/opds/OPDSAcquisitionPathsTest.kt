package org.nypl.simplified.tests.opds

import one.irradia.mime.api.MIMEType
import one.irradia.mime.vanilla.MIMEParser.Companion.parseRaisingException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionPath
import org.nypl.simplified.opds.core.OPDSAcquisitionPathElement
import org.nypl.simplified.opds.core.OPDSAcquisitionPaths
import org.nypl.simplified.opds.core.OPDSIndirectAcquisition
import java.net.URI

class OPDSAcquisitionPathsTest {

  private fun mimeOf(text: String): MIMEType {
    return try {
      parseRaisingException(text)
    } catch (e: Exception) {
      throw IllegalStateException(e)
    }
  }

  private fun pathElementOf(
    mime: String,
    uri: String? = null,
    properties: Map<String, String> = emptyMap()
  ): OPDSAcquisitionPathElement {
    return OPDSAcquisitionPathElement(
      mimeType = this.mimeOf(mime),
      target = uri?.let { URI.create(it) },
      properties = properties
    )
  }

  @Test
  fun testEmpty() {
    assertEquals(listOf<OPDSAcquisitionPath>(), OPDSAcquisitionPaths.linearize(listOf()))
  }

  @Test
  fun testAcquisitionDirect() {
    val acquisition =
      OPDSAcquisition(
        relation = OPDSAcquisition.Relation.ACQUISITION_GENERIC,
        uri = URI.create("http://www.example.com"),
        type = this.mimeOf("application/epub+zip"),
        indirectAcquisitions = listOf(),
        properties = emptyMap()
      )

    val element0 =
      this.pathElementOf("application/epub+zip", "http://www.example.com")
    val path =
      OPDSAcquisitionPath(acquisition, listOf(element0))

    val linearized =
      OPDSAcquisitionPaths.linearize(acquisition)

    assertEquals(path, linearized[0])
    assertEquals(1, linearized.size)
  }

  @Test
  fun testAcquisitionAdobeIndirect() {
    val acquisition =
      OPDSAcquisition(
        relation = OPDSAcquisition.Relation.ACQUISITION_GENERIC,
        uri = URI.create("http://www.example.com"),
        type = this.mimeOf("application/vnd.adobe.adept+xml"),
        indirectAcquisitions = listOf(
          OPDSIndirectAcquisition(this.mimeOf("application/epub+zip"), listOf(), emptyMap()),
          OPDSIndirectAcquisition(this.mimeOf("application/pdf"), listOf(), emptyMap())
        ),
        properties = emptyMap()
      )

    val element0 =
      this.pathElementOf("application/vnd.adobe.adept+xml", "http://www.example.com")
    val element01 =
      this.pathElementOf("application/epub+zip")
    val element02 =
      this.pathElementOf("application/pdf")

    val path0 =
      OPDSAcquisitionPath(acquisition, listOf(element0, element01))
    val path1 =
      OPDSAcquisitionPath(acquisition, listOf(element0, element02))

    val linearized =
      OPDSAcquisitionPaths.linearize(acquisition)

    assertEquals(path0, linearized[0])
    assertEquals(path1, linearized[1])
    assertEquals(2, linearized.size)
  }

  @Test
  fun testAcquisitionPathProperties() {
    val acquisition =
      OPDSAcquisition(
        relation = OPDSAcquisition.Relation.ACQUISITION_GENERIC,
        uri = URI.create("http://cm.se-community-lcp-test.lyrtech.org/LCP/works/10/fulfill/31"),
        type = this.mimeOf("application/vnd.readium.lcp.license.v1.0+json"),
        indirectAcquisitions = listOf(
          OPDSIndirectAcquisition(this.mimeOf("application/epub+zip"), listOf(), emptyMap()),
        ),
        properties = mapOf(
          Pair("lcp:hashed_passphrase", "eb4961889d0c1329d8f31b1d73ed3ad60f2ef11b06692ac42968a5f076289707")
        )
      )

    val element0 =
      this.pathElementOf(
        mime = "application/vnd.readium.lcp.license.v1.0+json",
        uri = "http://cm.se-community-lcp-test.lyrtech.org/LCP/works/10/fulfill/31",
        properties = acquisition.properties
      )
    val element01 =
      this.pathElementOf("application/epub+zip")

    val path0 =
      OPDSAcquisitionPath(acquisition, listOf(element0, element01))

    val linearized =
      OPDSAcquisitionPaths.linearize(acquisition)

    assertEquals(path0, linearized[0])
    assertEquals(1, linearized.size)
  }
}
