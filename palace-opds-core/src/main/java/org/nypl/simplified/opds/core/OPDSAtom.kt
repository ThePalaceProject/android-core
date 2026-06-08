package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import org.nypl.simplified.opds.core.OPDSXML.getFirstChildElementTextWithName
import org.nypl.simplified.opds.core.OPDSXML.getFirstChildElementWithNameOptional
import org.w3c.dom.DOMException
import org.w3c.dom.Element
import java.text.ParseException

object OPDSAtom {

  @JvmStatic
  @Throws(OPDSParseException::class)
  fun findID(
    ee: Element
  ): String {
    return getFirstChildElementTextWithName(
      ee, OPDSFeedConstants.ATOM_URI, "id"
    )
  }

  @JvmStatic
  @Throws(DOMException::class, ParseException::class)
  fun findPublished(
    e: Element
  ): DateTime? {
    val eOpt: Element? =
      getFirstChildElementWithNameOptional(
        e, OPDSFeedConstants.DUBLIN_CORE_TERMS_URI, "issued"
      )

    return eOpt?.let { er ->
      val text = er.textContent
      val trimmed = text.trim { it <= ' ' }
      OPDSDateParsers.dateTimeParser().parseDateTime(trimmed)
    }
  }

  @JvmStatic
  @Throws(OPDSParseException::class)
  fun findTitle(
    e: Element
  ): String {
    return getFirstChildElementTextWithName(
      e, OPDSFeedConstants.ATOM_URI, "title"
    )
  }

  @JvmStatic
  @Throws(OPDSParseException::class)
  fun findUpdated(
    e: Element
  ): DateTime {
    val eUpdatedRaw =
      getFirstChildElementTextWithName(e, OPDSFeedConstants.ATOM_URI, "updated")
    return OPDSDateParsers.dateTimeParser().parseDateTime(eUpdatedRaw)
  }
}
