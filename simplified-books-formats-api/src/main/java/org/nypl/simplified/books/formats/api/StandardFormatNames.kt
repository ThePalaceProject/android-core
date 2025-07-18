package org.nypl.simplified.books.formats.api

import one.irradia.mime.api.MIMEType
import one.irradia.mime.vanilla.MIMEParser

/**
 * A registry of known MIME types.
 */

object StandardFormatNames {

  private fun mimeOf(name: String): MIMEType =
    MIMEParser.parseRaisingException(name)

  private fun mimesOfList(names: List<String>): Set<MIMEType> =
    names.map(this::mimeOf).toSet()

  private fun mimesOf(vararg names: String): Set<MIMEType> =
    this.mimesOfList(names.toList())

  /**
   * The standard format name for Simplified bearer tokens.
   */

  val simplifiedBearerToken: MIMEType =
    mimeOf("application/vnd.librarysimplified.bearer-token+json")

  /**
   * The standard format name for Findaway audio books.
   *
   * @see "https://findaway.com"
   */

  val findawayAudioBooks =
    this.mimeOf("application/vnd.librarysimplified.findaway.license+json")

  /**
   * The standard format name for Overdrive audio books.
   */

  val overdriveAudioBooks =
    this.mimeOf("application/vnd.overdrive.circulation.api+json;profile=audiobook")

  /**
   * The standard format name for DPLA audio books.
   */

  val dplaAudioBooks =
    this.mimeOf("application/audiobook+json;profile=http://www.feedbooks.com/audiobooks/access-restriction")

  val lcpAudioBooks =
    this.mimeOf("application/audiobook+lcp")

  /**
   * Various standard format names used for generic, unencrypted audio books.
   */

  val genericAudioBooks =
    this.mimesOf(
      "application/audiobook+json",
      "audio/mpeg"
    )

  /**
   * The union of all of the known standard format names used for audio books.
   */

  val allAudioBooks: Set<MIMEType> =
    this.run {
      val types = mutableSetOf<MIMEType>()
      types.add(this.findawayAudioBooks)
      types.add(this.overdriveAudioBooks)
      types.add(this.dplaAudioBooks)
      types.add(this.lcpAudioBooks)
      types.addAll(this.genericAudioBooks)
      types.toSet()
    }

  /**
   * The MIME type used for LCP licenses.
   */

  val lcpLicenseFiles =
    this.mimeOf("application/vnd.readium.lcp.license.v1.0+json")

  /**
   * The MIME type used for Boundless licenses.
   */

  val boundlessLicenseFiles =
    this.mimeOf("application/vnd.thepalaceproject.baker-taylor.kdrm+json")

  /**
   * The MIME type used for Adobe ACSM files.
   */

  val adobeACSMFiles =
    this.mimeOf("application/vnd.adobe.adept+xml")

  /**
   * The MIME type used for EPUB files, encrypted or otherwise.
   */

  val genericEPUBFiles =
    this.mimeOf("application/epub+zip")

  /**
   * The MIME type used for text/html book files.
   */

  val textHtmlBook =
    this.mimeOf("text/html")

  /**
   * The MIME type used for mp3 audiobook files.
   */

  val mpegAudioBooks =
    this.mimeOf("audio/mpeg")

  /**
   * The MIME type used for wma audiobook files.
   */

  val wmaAudioBooks =
    this.mimeOf("audio/x-ms-wma")

  /**
   * The MIME types used for book preview files.
   */

  val bookPreviewFiles = listOf(
    textHtmlBook,
    genericEPUBFiles,
    mpegAudioBooks,
    wmaAudioBooks
  )

  /**
   * The MIME type used for PDF files, encrypted or otherwise.
   */

  val genericPDFFiles =
    this.mimeOf("application/pdf")

  /**
   * The MIME type used for ZIP files.
   */

  val genericZIPFiles =
    this.mimeOf("application/zip")

  /**
   * The MIME type used for OPDS feeds.
   */

  val opdsFeed =
    this.mimeOf("application/atom+xml")

  /**
   * The MIME type used for OPDS feeds.
   */

  val opdsFeedCatalog =
    this.mimeOf("application/atom+xml;profile=opds-catalog")

  /**
   * The MIME type used for OPDS feed entries.
   */

  val opdsAcquisitionFeedEntry =
    this.mimeOf("application/atom+xml;type=entry;profile=opds-catalog")

  /**
   * The MIME type used for OPDS feed entries (Old style).
   */

  val opdsAcquisitionFeedEntryOld =
    this.mimeOf("application/atom+xml;relation=entry;profile=opds-catalog")

  /**
   * The union of all of the known standard format names used for OPDS feeds.
   */

  val allOPDSFeeds: Set<MIMEType> =
    this.run {
      val types = mutableSetOf<MIMEType>()
      types.add(this.opdsFeed)
      types.add(this.opdsFeedCatalog)
      types.add(this.opdsAcquisitionFeedEntry)
      types.add(this.opdsAcquisitionFeedEntryOld)
      types.toSet()
    }
}
