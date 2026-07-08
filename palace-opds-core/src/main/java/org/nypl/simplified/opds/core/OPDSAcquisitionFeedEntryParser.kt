package org.nypl.simplified.opds.core

import one.irradia.mime.api.MIMEType
import one.irradia.mime.vanilla.MIMEParser.Companion.parseRaisingException
import org.nypl.simplified.links.Link
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry.Companion.newBuilder
import org.nypl.simplified.parser.api.ParseError
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.text.ParseException
import java.util.Locale
import java.util.Map
import java.util.Objects
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * The default implementation of the [OPDSAcquisitionFeedEntryParserType]
 * type.
 */
class OPDSAcquisitionFeedEntryParser private constructor() : OPDSAcquisitionFeedEntryParserType {
  private fun findAcquisitionAuthors(
    element: Element,
    eb: OPDSAcquisitionFeedEntryBuilderType
  ) {
    val eAuthors =
      OPDSXML.getChildElementsWithName(element, OPDSFeedConstants.ATOM_URI, "author")
    for (ea in eAuthors) {
      val name =
        OPDSXML.getFirstChildElementWithNameOptional(
          Objects.requireNonNull(ea), OPDSFeedConstants.ATOM_URI, "name"
        )

      if (name != null) {
        eb.addAuthor(name.textContent)
      }
    }
  }

  @Throws(OPDSParseException::class)
  private fun findNarrators(
    element: Element,
    eb: OPDSAcquisitionFeedEntryBuilderType
  ) {
    val e_contributors =
      OPDSXML.getChildElementsWithName(element, OPDSFeedConstants.ATOM_URI, "contributor")
    for (ec in e_contributors) {
      Objects.requireNonNull(ec)
      if (ec.hasAttribute("opf:role")) {
        val role = ec.getAttribute("opf:role")
        if (role.lowercase() == "nrt") {
          val narratorName =
            OPDSXML.getFirstChildElementTextWithName(
              Objects.requireNonNull(ec),
              OPDSFeedConstants.ATOM_URI,
              "name"
            )
          eb.addNarrator(narratorName)
        }
      }
    }
  }

  private fun findPublisher(element: Element): String? =
    OPDSXML.getFirstChildElementTextWithNameOptional(
      element, OPDSFeedConstants.DUBLIN_CORE_TERMS_URI, "publisher"
    )

  private fun findDistribution(element: Element): String =
    OPDSXML.getFirstChildElementTextWithName(
      element, OPDSFeedConstants.BIBFRAME_URI, "distribution", "ProviderName"
    )

  @Throws(OPDSParseException::class, ParseException::class)
  private fun parseAcquisitionEntry(
    source: URI,
    element: Element
  ): OPDSAcquisitionFeedEntry {
    val id =
      OPDSAtom.findID(element)
    val title =
      OPDSAtom.findTitle(element)
    val updated =
      OPDSAtom.findUpdated(element)

    val entryBuilder =
      newBuilder(id, title, updated, OPDSAvailabilityLoanable.get())

    val eLinks =
      OPDSXML.getChildElementsWithNamesNonEmpty(element, LINK_NAMES)

    /*
     * First, locate a revocation link, if any. This is required to be found
     * first as it needs to be used later in availability information.
     */
    val revoke = findRevocationLink(source, entryBuilder, eLinks)

    /*
     * Now, handle any other types of links.
     */
    for (eLink in eLinks) {
      if (eLink.hasAttribute("rel")) {
        val relText = Objects.requireNonNull(eLink.getAttribute("rel"))

        if (tryConsumeLinkGroup(source, entryBuilder, eLink, relText)) {
          continue
        }

        if (tryConsumeLinkIssues(source, entryBuilder, eLink, relText)) {
          continue
        }

        if (tryConsumeLinkAlternate(source, entryBuilder, eLink, relText)) {
          continue
        }

        if (tryConsumeLinkAnalytics(source, entryBuilder, eLink, relText)) {
          continue
        }

        if (tryConsumeLinkRelated(source, entryBuilder, eLink, relText)) {
          continue
        }

        if (tryConsumeLinkAnnotation(source, entryBuilder, eLink, relText)) {
          continue
        }

        if (tryConsumeLinkThumbnail(source, entryBuilder, eLink, relText)) {
          continue
        }

        if (tryConsumeLinkCover(source, entryBuilder, eLink, relText)) {
          continue
        }

        if (tryConsumeLinkPreview(source, entryBuilder, eLink, relText)) {
          continue
        }

        if (tryConsumeLinkTimeTracking(source, entryBuilder, eLink, relText)) {
          continue
        }

        tryConsumeAcquisitions(source, entryBuilder, revoke, eLink, relText)
      }
    }

    parseCategories(element, entryBuilder)
    findAcquisitionAuthors(element, entryBuilder)
    findNarrators(element, entryBuilder)
    entryBuilder.setDurationOption(findDuration(element))
    entryBuilder.setPublisherOption(findPublisher(element))
    entryBuilder.setDistribution(findDistribution(element))
    entryBuilder.setPublishedOption(OPDSAtom.findPublished(element))
    entryBuilder.setLanguageOption(findLanguage(element))
    entryBuilder.setSummaryOption(
      OPDSXML.getFirstChildElementTextWithNameOptional(
        element,
        OPDSFeedConstants.ATOM_URI,
        "summary"
      )
    )
    findSeries(element, entryBuilder)
    return entryBuilder.build()
  }

  private fun findSeries(
    element: Element,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType
  ) {
    val child: Element?

    try {
      child =
        OPDSXML.getFirstChildElementWithNameOptional(
          node = element,
          namespace = OPDSFeedConstants.SCHEMA_URI,
          name = "series"
        )

      if (child == null) {
        return
      }

      val nameText =
        child.getAttribute("name")

      val positionText =
        OPDSXML.getFirstChildElementTextWithNameOptional(
          node = child,
          namespace = OPDSFeedConstants.ATOM_URI,
          name = "position"
        )

      val eLinks =
        OPDSXML.getChildElementsWithNamesNonEmpty(child, LINK_NAMES)

      for (eLink in eLinks) {
        if (eLink.hasAttribute("rel")) {
          val relText = eLink.getAttribute("rel")
          when (relText) {
            "series" -> {
              entryBuilder.setSeries(
                OPDSSeries(
                  position = positionText?.toInt(),
                  name = nameText,
                  link = URI.create(eLink.getAttribute("href"))
                )
              )
              return
            }
          }
        }
      }
    } catch (e: Exception) {
      LOG.warn("Failed to parse OPDS series: ", e)
    }
  }

  private fun findLanguage(element: Element): String? {
    return try {
      val child =
        OPDSXML.getFirstChildElementTextWithNameOptional(
          element,
          OPDSFeedConstants.DUBLIN_CORE_TERMS_URI,
          "language"
        )

      if (child != null) {
        val locale = Locale.forLanguageTag(child)
        if (locale != null) {
          return locale.displayLanguage
        }
      }

      null
    } catch (_: Exception) {
      null
    }
  }

  private fun findDuration(element: Element): Double? {
    try {
      return OPDSXML
        .getFirstChildElementTextWithNameOptional(
          element, OPDSFeedConstants.DUBLIN_CORE_TERMS_URI, "duration"
        )?.toDouble()
    } catch (_: Exception) {
      return null
    }
  }

  @Throws(OPDSParseException::class)
  private fun tryConsumeDRMLicensorInformation(
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element
  ) {
    val licensor =
      OPDSXML.getFirstChildElementWithNameOptional(
        eLink, OPDSFeedConstants.DRM_URI, "licensor"
      )

    if (licensor != null) {
      val licensorElement = OPDSXML.nodeAsElement(licensor)
      val vendor = licensorElement.getAttribute("drm:vendor")
      var clientToken: String? = null
      var deviceManager: String? = null

      val licensorChildren = licensorElement.childNodes
      for (i in 0..<licensorChildren.length) {
        val node = licensorChildren.item(i)

        if (node.nodeName.contains("clientToken")) {
          clientToken = node.firstChild.nodeValue
        }

        if (node.nodeName.contains("link")) {
          val element = OPDSXML.nodeAsElement(node)
          val hasEverything =
            element.hasAttribute("rel") && element.hasAttribute("href")

          if (hasEverything) {
            val r: String = Objects.requireNonNull(element.getAttribute("rel"))
            val h: String = Objects.requireNonNull(element.getAttribute("href"))

            if ("http://librarysimplified.org/terms/drm/rel/devices" == r) {
              deviceManager = h
            }
          }
        }

        if (vendor != null && clientToken != null) {
          val licensor = DRMLicensor(vendor, clientToken, deviceManager)
          entryBuilder.setLicensorOption(licensor)
        }
      }
    }
  }

  @Throws(OPDSParseException::class)
  private fun parseIndirectAcquisition(acquisition: Element): OPDSIndirectAcquisition {
    try {
      val attributeText =
        acquisition.getAttribute("type")
      val extraProperties: MutableMap<String, String> =
        consumeExtraAcquisitionProperties(acquisition)
      val type =
        parseRaisingException(attributeText)
      val nextAcquisitions =
        parseIndirectAcquisitions(acquisition)
      return OPDSIndirectAcquisition(type, nextAcquisitions, extraProperties)
    } catch (e: Exception) {
      throw OPDSParseException(e)
    }
  }

  @Throws(OPDSParseException::class)
  private fun parseIndirectAcquisitions(element: Element): MutableList<OPDSIndirectAcquisition> {
    val indirectElements =
      OPDSXML.getChildElementsWithName(
        element,
        OPDSFeedConstants.OPDS_URI,
        "indirectAcquisition"
      )
    val indirects: MutableList<OPDSIndirectAcquisition> =
      ArrayList(indirectElements.size)

    for (indirectElement in indirectElements) {
      indirects.add(parseIndirectAcquisition(indirectElement))
    }
    return indirects
  }

  @Throws(OPDSParseException::class)
  private fun tryConsumeAcquisitions(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    revoke: URI?,
    link: Element,
    relText: String
  ) {
    val linkIsTemplated =
      link.localName == ODL_TEMPLATED_LINK

    if (relText.startsWith(OPDSFeedConstants.ACQUISITION_URI_PREFIX_TEXT)) {
      for (v in OPDSAcquisition.Relation.entries) {
        val uriText: String = v.uri.toString()
        if (relText == uriText) {
          val indirects =
            parseIndirectAcquisitions(link)
          val type =
            typeAttributeWithSupportedValue(link)

          /*
           * Links without types are ignored.
           */

          if (type == null) {
            continue
          }

          val extraProperties: MutableMap<String, String> =
            consumeExtraAcquisitionProperties(link)

          val resultingLink: Link
          if (!linkIsTemplated) {
            val href: URI
            try {
              href = scrubURI(source, link.getAttribute("href"))
            } catch (e: URISyntaxException) {
              entryBuilder.addParseError(
                this.invalidURI(
                  source,
                  "'href' attribute of element with relation " + v,
                  e
                )
              )
              continue
            }

            resultingLink =
              Link.LinkBasic(
                href,
                type,
                null,
                null,
                null,
                null,
                null,
                null
              )
          } else {
            resultingLink =
              Link.LinkTemplated(
                link.getAttribute("href"),
                type,
                null,
                null,
                null,
                null,
                null,
                null
              )
          }

          val acquisition =
            OPDSAcquisition(v, resultingLink, type, indirects, extraProperties)
          entryBuilder.addAcquisition(acquisition)

          if (v == OPDSAcquisition.Relation.ACQUISITION_OPEN_ACCESS) {
            entryBuilder.setAvailability(OPDSAvailabilityOpenAccess.get(revoke))
          } else {
            tryAvailability(entryBuilder, link, revoke)
          }
          break
        }
      }

      tryConsumeDRMLicensorInformation(entryBuilder, link)
    }
  }

  private fun parseCategories(
    element: Element,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType
  ) {
    val eCategories =
      OPDSXML.getChildElementsWithName(element, OPDSFeedConstants.ATOM_URI, "category")

    for (ce in eCategories) {
      val term =
        Objects.requireNonNull(ce.getAttribute("term"))
      val scheme =
        Objects.requireNonNull(ce.getAttribute("scheme"))

      val label: String? =
        if (ce.hasAttribute("label")) {
          ce.getAttribute("label")
        } else {
          null
        }

      entryBuilder.addCategory(OPDSCategory(term, scheme, label))
      if (scheme == "http://schema.org/audience") {
        entryBuilder.setAudienceOption(term)
      }
    }
  }

  /**
   * Check if the given link refers to a cover image. If it is, add it to the builder and
   * return `true`.
   */
  private fun tryConsumeLinkCover(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element,
    relText: String
  ): Boolean {
    if (relText == OPDSFeedConstants.IMAGE_URI_TEXT) {
      if (eLink.hasAttribute("href")) {
        try {
          val u: URI = scrubURI(source, eLink.getAttribute("href"))
          entryBuilder.setCoverOption(u)
        } catch (e: URISyntaxException) {
          entryBuilder.addParseError(
            this.invalidURI(
              source,
              hrefAttributeOfLinkRel(OPDSFeedConstants.IMAGE_URI_TEXT),
              e
            )
          )
        }
        return true
      }
    }
    return false
  }

  /**
   * Check if the given link refers to a preview or sample. If it is, add it to the builder and
   * return `true`.
   */
  private fun tryConsumeLinkPreview(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element,
    relText: String
  ): Boolean {
    if (relText == OPDSFeedConstants.SAMPLE_TEXT || relText == OPDSFeedConstants.PREVIEW_TEXT) {
      if (eLink.hasAttribute("href")) {
        try {
          val u: URI = scrubURI(source, eLink.getAttribute("href"))
          val type = typeAttributeWithSupportedValue(eLink)!!
          entryBuilder.addPreviewAcquisition(OPDSPreviewAcquisition(u, type))
        } catch (e: URISyntaxException) {
          entryBuilder.addParseError(
            this.invalidURI(
              source,
              hrefAttributeOfLinkRel(
                if (relText == OPDSFeedConstants.SAMPLE_TEXT) {
                  OPDSFeedConstants.SAMPLE_TEXT
                } else {
                  OPDSFeedConstants.PREVIEW_TEXT
                }
              ),
              e
            )
          )
        }
        return true
      }
    }
    return false
  }

  /**
   * Check if the given link refers to the time tracking uri. If it is, add it to the builder and
   * return `true`.
   */
  private fun tryConsumeLinkTimeTracking(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element,
    relText: String
  ): Boolean {
    if (relText == OPDSFeedConstants.TIME_TRACKING_URI_TEXT) {
      if (eLink.hasAttribute("href")) {
        try {
          val u: URI = scrubURI(source, eLink.getAttribute("href"))
          entryBuilder.setTimeTrackingUriOption(u)
        } catch (e: URISyntaxException) {
          entryBuilder.addParseError(
            this.invalidURI(
              source,
              hrefAttributeOfLinkRel(OPDSFeedConstants.TIME_TRACKING_URI_TEXT),
              e
            )
          )
        }
        return true
      }
    }
    return false
  }

  /**
   * Check if the given link refers to a thumbnail. If it is, add it to the builder and
   * return `true`.
   */
  private fun tryConsumeLinkThumbnail(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element,
    relText: String
  ): Boolean {
    if (relText == OPDSFeedConstants.THUMBNAIL_URI_TEXT) {
      if (eLink.hasAttribute("href")) {
        try {
          val u: URI = scrubURI(source, eLink.getAttribute("href"))
          entryBuilder.setThumbnailOption(u)
        } catch (e: URISyntaxException) {
          entryBuilder.addParseError(
            this.invalidURI(
              source,
              hrefAttributeOfLinkRel(OPDSFeedConstants.THUMBNAIL_URI_TEXT),
              e
            )
          )
        }
        return true
      }
    }
    return false
  }

  /**
   * Check if the given link refers to an annotation. If it does, add it to the builder and
   * return `true`.
   */
  private fun tryConsumeLinkAnnotation(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element,
    relText: String
  ): Boolean {
    if (relText == OPDSFeedConstants.ANNOTATION_URI_TEXT) {
      if (eLink.hasAttribute("href")) {
        try {
          val u: URI = scrubURI(source, eLink.getAttribute("href"))
          entryBuilder.setAnnotationsOption(u)
        } catch (e: URISyntaxException) {
          entryBuilder.addParseError(
            this.invalidURI(
              source,
              hrefAttributeOfLinkRel(OPDSFeedConstants.ANNOTATION_URI_TEXT),
              e
            )
          )
        }
        return true
      }
    }
    return false
  }

  /**
   * Check if the given link refers to related books. If it does, add it to the builder and
   * return `true`.
   */
  private fun tryConsumeLinkRelated(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element,
    relText: String
  ): Boolean {
    if (relText == OPDSFeedConstants.RELATED_REL_TEXT) {
      if (eLink.hasAttribute("href")) {
        try {
          val u: URI = scrubURI(source, eLink.getAttribute("href"))
          entryBuilder.setRelatedOption(u)
        } catch (e: URISyntaxException) {
          entryBuilder.addParseError(
            this.invalidURI(
              source,
              hrefAttributeOfLinkRel(OPDSFeedConstants.RELATED_REL_TEXT),
              e
            )
          )
        }
        return true
      }
    }
    return false
  }

  /**
   * Check if the given link refers to an "alternate". If it does, add it to the builder and
   * return `true`.
   */
  private fun tryConsumeLinkAlternate(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element,
    relText: String
  ): Boolean {
    if (relText == OPDSFeedConstants.ALTERNATE_REL_TEXT) {
      try {
        val uriText = Objects.requireNonNull(eLink.getAttribute("href"))
        val uri: URI = scrubURI(source, uriText)
        entryBuilder.setAlternateOption(uri)
      } catch (e: URISyntaxException) {
        entryBuilder.addParseError(
          this.invalidURI(
            source,
            hrefAttributeOfLinkRel(OPDSFeedConstants.ALTERNATE_REL_TEXT),
            e
          )
        )
      }
      return true
    }
    return false
  }

  /**
   * Check if the given link refers to analytics. If it does, add it to the builder and
   * return `true`.
   */
  private fun tryConsumeLinkAnalytics(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element,
    relText: String
  ): Boolean {
    if (relText == OPDSFeedConstants.CIRCULATION_ANALYTICS_OPEN_BOOK_REL_TEXT) {
      try {
        val uriText = Objects.requireNonNull(eLink.getAttribute("href"))
        val uri: URI = scrubURI(source, uriText)
        entryBuilder.setAnalyticsOption(uri)
      } catch (e: URISyntaxException) {
        entryBuilder.addParseError(
          this.invalidURI(
            source,
            hrefAttributeOfLinkRel(OPDSFeedConstants.CIRCULATION_ANALYTICS_OPEN_BOOK_REL_TEXT),
            e
          )
        )
      }
      return true
    }
    return false
  }

  /**
   * Check if the given link refers to an issue system. If it does, add it to the builder and
   * return `true`.
   */
  private fun tryConsumeLinkIssues(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element,
    relText: String
  ): Boolean {
    if (relText == OPDSFeedConstants.ISSUES_REL_TEXT) {
      try {
        val uriText = Objects.requireNonNull(eLink.getAttribute("href"))
        val uri: URI = scrubURI(source, uriText)
        entryBuilder.setIssuesOption(uri)
      } catch (e: URISyntaxException) {
        entryBuilder.addParseError(
          this.invalidURI(
            source,
            hrefAttributeOfLinkRel(OPDSFeedConstants.ISSUES_REL_TEXT),
            e
          )
        )
      }
      return true
    }
    return false
  }

  /**
   * Check if the given link refers to a group. If it does, add it to the builder and
   * return `true`.
   */
  private fun tryConsumeLinkGroup(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLink: Element,
    relText: String
  ): Boolean {
    if (relText == OPDSFeedConstants.GROUP_REL_TEXT) {
      try {
        val uriText = Objects.requireNonNull(eLink.getAttribute("href"))
        val linkTitle = Objects.requireNonNull(eLink.getAttribute("title"))
        val uri: URI = scrubURI(source, uriText)
        entryBuilder.addGroup(uri, linkTitle)
      } catch (e: URISyntaxException) {
        entryBuilder.addParseError(
          this.invalidURI(
            source,
            hrefAttributeOfLinkRel(OPDSFeedConstants.GROUP_REL_TEXT),
            e
          )
        )
      }
      return true
    }
    return false
  }

  private fun invalidURI(
    source: URI,
    sourceLocation: String?,
    e: Exception
  ): ParseError {
    val builder = StringBuilder(128)
    builder.append("Could not parse URI: ")
    builder.append(sourceLocation)
    builder.append(": ")
    builder.append(e.message)

    return ParseError(
      source,
      builder.toString(),
      -1,
      0,
      e
    )
  }

  private fun findRevocationLink(
    source: URI,
    entryBuilder: OPDSAcquisitionFeedEntryBuilderType,
    eLinks: MutableList<Element>
  ): URI? {
    for (eLink in eLinks) {
      if (eLink.hasAttribute("rel")) {
        val relText = Objects.requireNonNull(eLink.getAttribute("rel"))
        if (relText == OPDSFeedConstants.REVOKE_URI_TEXT) {
          if (eLink.hasAttribute("href")) {
            try {
              val u: URI = scrubURI(source, eLink.getAttribute("href"))
              return u
            } catch (e: URISyntaxException) {
              entryBuilder.addParseError(
                this.invalidURI(
                  source,
                  hrefAttributeOfLinkRel(OPDSFeedConstants.REVOKE_URI_TEXT),
                  e
                )
              )
              return null
            }
          }
        }
      }
    }
    return null
  }

  private fun typeAttributeWithSupportedValue(acquisition: Element): MIMEType? {
    val attributeText = acquisition.getAttribute("type")
    if (attributeText == null) {
      return null
    }
    if (attributeText.isEmpty()) {
      return null
    }

    try {
      return parseRaisingException(attributeText)
    } catch (e: Exception) {
      LOG.warn("Unparseable MIME type: ", e)
      return null
    }
  }

  @Throws(OPDSParseException::class)
  private fun tryAvailability(
    eb: OPDSAcquisitionFeedEntryBuilderType,
    element: Element,
    revoke: URI?
  ) {
    val copies =
      OPDSXML.getFirstChildElementWithNameOptional(
        element,
        OPDSFeedConstants.OPDS_URI,
        "copies"
      )
    val holds =
      OPDSXML.getFirstChildElementWithNameOptional(
        element,
        OPDSFeedConstants.OPDS_URI,
        "holds"
      )

    eb.setAvailability(inferAvailability(element, copies, holds, revoke))
  }

  @Throws(OPDSParseException::class)
  private fun inferAvailability(
    element: Element,
    copies: Element?,
    holds: Element?,
    revoke: URI?
  ): OPDSAvailabilityType {
    val available =
      OPDSXML.getFirstChildElementWithNameOptional(
        element,
        OPDSFeedConstants.OPDS_URI,
        "availability"
      )

    if (available != null) {
      val status = available.getAttribute("status")

      if ("ready" == status) {
        val endDate = OPDSXML.getAttributeRFC3339Optional(available, "until")
        return OPDSAvailabilityHeldReady.get(endDate, revoke)
      }

      if ("reserved" == status) {
        val endDate =
          OPDSXML.getAttributeRFC3339Optional(available, "until")
        val startDate =
          OPDSXML.getAttributeRFC3339Optional(available, "since")

        var queue: Int? = null
        if (holds != null) {
          queue = OPDSXML.getAttributeIntegerOptional(holds, "position")
        }
        return OPDSAvailabilityHeld.get(startDate, queue, endDate, revoke)
      }

      if ("available" == status) {
        val endDate =
          OPDSXML.getAttributeRFC3339Optional(available, "until")
        val startDate =
          OPDSXML.getAttributeRFC3339Optional(available, "since")
        val rel = Objects.requireNonNull(element.getAttribute("rel"))
        if (OPDSAcquisition.Relation.ACQUISITION_BORROW.uri
            .toString() == rel) {
          return OPDSAvailabilityLoanable.get()
        } else if (OPDSAcquisition.Relation.ACQUISITION_GENERIC.uri
            .toString() == rel) {
          return OPDSAvailabilityLoaned.get(startDate, endDate, revoke)
        }
      }
    }

    /*
     * The user has never seen the book before, and the book
     * did not have an availability:available element for its
     * borrow link, so it must be holdable.
     */

    if (copies != null) {
      val copiesAvailable =
        OPDSXML.getAttributeInteger(copies, "available")

      if (copiesAvailable > 0) {
        return OPDSAvailabilityLoanable.get()
      }
    }

    return OPDSAvailabilityHoldable.get()
  }

  @Throws(OPDSParseException::class)
  override fun parseEntry(
    source: URI,
    element: Element
  ): OPDSAcquisitionFeedEntry {
    Objects.requireNonNull(element, "Element")
    try {
      val entry = parseAcquisitionEntry(source, element)
      for (error in entry.errors) {
        LOG.error("{}: parse error: {}: ", source, error.message, error.exception)
      }
      return entry
    } catch (ex: ParseException) {
      throw OPDSParseException(ex)
    }
  }

  @Throws(OPDSParseException::class)
  override fun parseEntryStream(
    source: URI,
    stream: InputStream
  ): OPDSAcquisitionFeedEntry {
    Objects.requireNonNull(stream, "Stream")
    try {
      val dbf = DocumentBuilderFactory.newInstance()
      dbf.isNamespaceAware = true
      val db = dbf.newDocumentBuilder()
      val d = Objects.requireNonNull(db.parse(stream))
      val e = Objects.requireNonNull(d.documentElement)
      return this.parseEntry(source, e)
    } catch (ex: ParserConfigurationException) {
      throw OPDSParseException(ex)
    } catch (ex: IOException) {
      throw OPDSParseException(ex)
    } catch (ex: SAXException) {
      throw OPDSParseException(ex)
    }
  }

  companion object {
    private val LOG: Logger =
      LoggerFactory.getLogger(OPDSAcquisitionFeedEntryParser::class.java)
    private const val ODL_TEMPLATED_LINK = "tlink"
    private val LINK_NAMES: HashSet<MutableMap.MutableEntry<URI, String>> =
      HashSet()

    init {
      LINK_NAMES.add(Map.entry<URI, String>(OPDSFeedConstants.ATOM_URI, "link"))
      LINK_NAMES.add(Map.entry<URI, String>(OPDSFeedConstants.ODL_URI, ODL_TEMPLATED_LINK))
    }

    /**
     * @return A new feed entry parser
     */
    @JvmStatic
    fun newParser(): OPDSAcquisitionFeedEntryParserType = OPDSAcquisitionFeedEntryParser()

    private fun consumeExtraAcquisitionProperties(link: Element): MutableMap<String, String> {
      val properties = HashMap<String, String>()

      val text =
        OPDSXML.getFirstChildElementTextWithNameOptional(
          link,
          OPDSFeedConstants.LCP_URI,
          "hashed_passphrase"
        )

      if (text != null) {
        properties["lcp:hashed_passphrase"] = text
      }
      return properties
    }

    @Throws(URISyntaxException::class)
    private fun scrubURI(
      base: URI,
      text: String
    ): URI {
      val unresolvedURI = URI(text.trim { it <= ' ' })
      if (unresolvedURI.isAbsolute) {
        return unresolvedURI
      }
      return base.resolve(unresolvedURI)
    }

    private fun hrefAttributeOfLinkRel(relValue: String): String = "'href' attribute of 'link' with 'rel' " + relValue
  }
}
