package org.nypl.simplified.opds.core

import com.google.common.base.Preconditions
import org.joda.time.DateTime
import org.nypl.simplified.opds.core.OPDSAcquisitionFeed.Companion.newBuilder
import org.nypl.simplified.opds.core.OPDSAtom.findID
import org.nypl.simplified.opds.core.OPDSAtom.findTitle
import org.nypl.simplified.opds.core.OPDSAtom.findUpdated
import org.nypl.simplified.opds.core.OPDSFeedConstants.ATOM_URI
import org.nypl.simplified.opds.core.OPDSFeedConstants.AUTHENTICATION_DOCUMENT_RELATION_URI_TEXT
import org.nypl.simplified.opds.core.OPDSFeedConstants.DRM_URI
import org.nypl.simplified.opds.core.OPDSFeedConstants.FACET_URI_TEXT
import org.nypl.simplified.opds.core.OPDSFeedConstants.OPDS_URI_TEXT
import org.nypl.simplified.opds.core.OPDSFeedConstants.SIMPLIFIED_URI_TEXT
import org.nypl.simplified.opds.core.OPDSXML.nodeAsElement
import org.nypl.simplified.opds.core.OPDSXML.nodeAsElementWithName
import org.nypl.simplified.opds.core.OPDSXML.nodeHasName
import org.nypl.simplified.parser.api.ParseError
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.DOMException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.Objects
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * The default implementation of the [OPDSFeedParserType].
 *
 * The implementation generally assumes that all sections of the OPDS
 * specification that are denoted as "SHOULD" will in practice mean "WILL NOT".
 */

class OPDSFeedParser private constructor(
  private val entryParser: OPDSAcquisitionFeedEntryParserType
) : OPDSFeedParserType {

  @Throws(OPDSParseException::class)
  override fun parse(
    uri: URI,
    s: InputStream?
  ): OPDSAcquisitionFeed {
    Objects.requireNonNull<InputStream?>(s)

    val timePreParse = System.nanoTime()
    var timePostParse = timePreParse

    try {
      LOG.debug("parsing: {}", uri)

      val d: Document = parseStream(s)
      timePostParse = System.nanoTime()

      val root = Objects.requireNonNull(d.firstChild)
      if (root is Element) {
        val root_e = root
        if (nodeHasName(root_e, ATOM_URI, "feed")) {
          return this.parseAsFeed(uri, root_e)
        }
        if (nodeHasName(root_e, ATOM_URI, "entry")) {
          return this.parseAsEntry(uri, root_e)
        }

        throw OPDSParseException(
          String.format(
            "Feed root is '%s', expected 'feed' or 'entry'",
            root_e.localName
          )
        )
      } else {
        throw OPDSParseException("Feed root is not 'feed' or 'entry'")
      }
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

  @Throws(OPDSParseException::class)
  private fun parseAsEntry(
    uri: URI,
    e: Element
  ): OPDSAcquisitionFeed {
    LOG.debug("parsing feed as single entry: {}", uri)

    val id = "urn:simplified-entry"
    val updated = DateTime.now()
    val title = "Entry"
    val b =
      newBuilder(uri, id, updated, title)
    val entry =
      this.entryParser.parseEntry(uri, e)

    if (!entry.acquisitions.isEmpty()) {
      b.addEntry(entry)
    }

    return b.build()
  }

  @Throws(OPDSParseException::class)
  private fun parseAsFeed(
    uri: URI,
    root: Node
  ): OPDSAcquisitionFeed {
    LOG.debug("parsing feed as ordinary feed: {}", uri)

    val eFeed = nodeAsElementWithName(root, ATOM_URI, "feed")
    val id = findID(eFeed)
    val title = findTitle(eFeed)
    val updated = findUpdated(eFeed)

    val builder =
      newBuilder(uri, id, updated, title)

    val links: MutableList<Element?> = ArrayList(32)
    val children = eFeed.childNodes

    for (index in 0..<children.length) {
      val child = Objects.requireNonNull(children.item(index))

      if (child is Element) {
        /*
         * Links.
         */
        if (nodeHasName(child, ATOM_URI, "link")) {
          val e = nodeAsElement(child)
          links.add(e)

          /*
           * Search links.
           */
          run {
            val r = parseSearchLink(uri, builder, e)
            if (r != null) {
              builder.setSearchOption(r)
              continue
            }
          }

          /*
           * Next links.
           */
          run {
            val r = parseNextLink(uri, builder, e)
            if (r != null) {
              builder.setNextOption(r)
              continue
            }
          }

          /*
           * Facet links.
           */
          run {
            val r = parseFacet(uri, builder, e)
            if (r != null) {
              builder.addFacet(r)
              continue
            }
          }

          /*
           * App About links.
           */
          run {
            val r = parseAbout(uri, builder, e)
            if (r != null) {
              builder.setAboutOption(r)
              continue
            }
          }

          /*
           * Terms of service links.
           */
          run {
            val r = parseTermsOfService(uri, builder, e)
            if (r != null) {
              builder.setTermsOfServiceOption(r)
              continue
            }
          }

          /*
           * Privacy policy links.
           */
          run {
            val r = parsePrivacyPolicy(uri, builder, e)
            if (r != null) {
              builder.setPrivacyPolicyOption(r)
              continue
            }
          }

          /*
           * Authentication document links.
           */
          run {
            val r = parseAuthenticationDocumentLink(uri, builder, e)
            if (r != null) {
              builder.setAuthenticationDocumentLink(r)
              continue
            }
          }

          /*
           * Annotations links.
           */
          run {
            val r = parseAnnotationsLink(uri, builder, e)
            if (r != null) {
              builder.setAnnotationsOption(r)
              continue
            }
          }

          continue
        }

        // parse licensor
        run {
          if (nodeHasName(
              child, DRM_URI, "licensor"
            )
          ) {
            val e = nodeAsElement(child)
            val vendor = e.getAttribute("drm:vendor")
            var clientToken: String? = null
            var deviceManager: String? = null
            for (i in 0..<e.childNodes.length) {
              val node = e.childNodes.item(i)

              if (node.nodeName.contains("clientToken")) {
                clientToken = node.firstChild.nodeValue
              }

              if (node.nodeName.contains("link")) {
                val element = nodeAsElement(node)

                val hasEverything =
                  element.hasAttribute("rel") && element.hasAttribute("href")

                if (hasEverything) {
                  val r =
                    Objects.requireNonNull(element.getAttribute("rel"))
                  val h =
                    Objects.requireNonNull(element.getAttribute("href"))

                  if ("http://librarysimplified.org/terms/drm/rel/devices" == r) {
                    deviceManager = h
                  }
                }
              }
              if (vendor != null && clientToken != null) {
                builder.setLicensor(DRMLicensor(vendor, clientToken, deviceManager))
              }
            }
          }
        }

        /*
         * Entries.
         */
        if (nodeHasName(child, ATOM_URI, "entry")) {
          val e = nodeAsElement(child)
          val entry = this.entryParser.parseEntry(uri, e)
          if (!entry.acquisitions.isEmpty()) {
            builder.addEntry(entry)
          }
        }
      }
    }

    return builder.build()
  }

  companion object {
    private val LOG: Logger =
      Objects.requireNonNull(LoggerFactory.getLogger(OPDSFeedParser::class.java))

    /**
     * @param entryParser A feed entry parser
     * @return A new feed  parser
     */
    @JvmStatic
    fun newParser(
      entryParser: OPDSAcquisitionFeedEntryParserType
    ): OPDSFeedParserType {
      return OPDSFeedParser(entryParser)
    }

    private fun parseFacet(
      source: URI,
      builder: OPDSAcquisitionFeedBuilderType,
      e: Element
    ): OPDSFacet? {
      val hasName = nodeHasName(e, ATOM_URI, "link")
      Preconditions.checkArgument(hasName, "Node has name 'link'")

      var hasEverything = e.hasAttribute("title")
      hasEverything = hasEverything && e.hasAttribute("href")
      hasEverything = hasEverything && e.hasAttribute("rel")
      hasEverything = hasEverything && e.hasAttributeNS(OPDS_URI_TEXT, "facetGroup")

      if (hasEverything) {
        val title: String =
          e.getAttribute("title")
        val rel: String =
          e.getAttribute("rel")
        val href: String =
          e.getAttribute("href")
        val group: String =
          e.getAttributeNS(OPDS_URI_TEXT, "facetGroup")

        if (FACET_URI_TEXT == rel) {
          val groupType: String? = parseFacetGroupType(e)
          val active: Boolean = parseFacetIsActive(e)
          try {
            return OPDSFacet(
              active,
              scrubURI(source, href),
              group,
              title,
              groupType
            )
          } catch (ex: URISyntaxException) {
            builder.addParseError(
              invalidURI(
                source,
                hrefAttributeOfLinkRel(FACET_URI_TEXT),
                ex
              )
            )
            return null
          }
        }
      }

      return null
    }

    private fun parseFacetGroupType(e: Element): String? {
      return if (e.hasAttributeNS(SIMPLIFIED_URI_TEXT, "facetGroupType")) {
        e.getAttributeNS(SIMPLIFIED_URI_TEXT, "facetGroupType")
      } else {
        null
      }
    }

    private fun parseFacetIsActive(e: Element): Boolean {
      if (e.hasAttributeNS(OPDS_URI_TEXT, "activeFacet")) {
        return e.getAttributeNS(OPDS_URI_TEXT, "activeFacet").toBoolean()
      } else {
        return false
      }
    }

    private fun parseNextLink(
      source: URI,
      builder: OPDSAcquisitionFeedBuilderType,
      e: Element
    ): URI? {
      Preconditions.checkArgument(
        "link" == e.localName,
        "localname %s == %s",
        e.localName,
        "link"
      )

      val rel = e.getAttribute("rel")
      if ("next" == rel) {
        if (e.hasAttribute("href")) {
          try {
            return scrubURI(source, e.getAttribute("href"))
          } catch (ex: URISyntaxException) {
            builder.addParseError(
              invalidURI(
                source,
                hrefAttributeOfLinkRel("next"),
                ex
              )
            )
            return null
          }
        }
      }

      return null
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

    private fun parseSearchLink(
      source: URI,
      builder: OPDSAcquisitionFeedBuilderType,
      e: Element
    ): OPDSSearchLink? {
      val hasName = nodeHasName(e, ATOM_URI, "link")
      Preconditions.checkArgument(hasName, "Node has name 'link'")

      val hasEverything =
        e.hasAttribute("type") && e.hasAttribute("rel") && e.hasAttribute("href")

      if (hasEverything) {
        val t = Objects.requireNonNull(e.getAttribute("type"))
        val r = Objects.requireNonNull(e.getAttribute("rel"))
        val h = Objects.requireNonNull(e.getAttribute("href"))

        if ("search" == r) {
          try {
            val u = Objects.requireNonNull(scrubURI(source, h))
            return OPDSSearchLink(t, u)
          } catch (ex: URISyntaxException) {
            builder.addParseError(
              invalidURI(
                source,
                hrefAttributeOfLinkRel("search"),
                ex
              )
            )
            return null
          }
        }
      }

      return null
    }

    @Throws(ParserConfigurationException::class, SAXException::class, IOException::class)
    private fun parseStream(
      s: InputStream?
    ): Document {
      val dbf = DocumentBuilderFactory.newInstance()
      dbf.isNamespaceAware = true
      val db = dbf.newDocumentBuilder()
      return Objects.requireNonNull(db.parse(s))
    }

    private fun parseTermsOfService(
      source: URI,
      builder: OPDSAcquisitionFeedBuilderType,
      e: Element
    ): URI? {
      val hasName = nodeHasName(e, ATOM_URI, "link")
      Preconditions.checkArgument(hasName, "Node has name 'link'")

      val hasEverything = e.hasAttribute("rel") && e.hasAttribute("href")
      if (hasEverything) {
        val r = Objects.requireNonNull(e.getAttribute("rel"))
        val h = Objects.requireNonNull(e.getAttribute("href"))

        if ("terms-of-service" == r) {
          try {
            return scrubURI(source, h)
          } catch (ex: URISyntaxException) {
            builder.addParseError(
              invalidURI(
                source,
                hrefAttributeOfLinkRel("terms-of-service"),
                ex
              )
            )
            return null
          }
        }
      }

      return null
    }

    private fun parseAbout(
      source: URI,
      builder: OPDSAcquisitionFeedBuilderType,
      e: Element
    ): URI? {
      val hasName = nodeHasName(e, ATOM_URI, "link")
      Preconditions.checkArgument(hasName, "Node has name 'link'")
      val hasEverything = e.hasAttribute("rel") && e.hasAttribute("href")
      if (hasEverything) {
        val r = Objects.requireNonNull(e.getAttribute("rel"))
        val h = Objects.requireNonNull(e.getAttribute("href"))

        if ("about" == r) {
          try {
            return scrubURI(source, h)
          } catch (ex: URISyntaxException) {
            builder.addParseError(
              invalidURI(
                source,
                hrefAttributeOfLinkRel("about"),
                ex
              )
            )
            return null
          }
        }
      }

      return null
    }

    private fun parsePrivacyPolicy(
      source: URI,
      builder: OPDSAcquisitionFeedBuilderType,
      e: Element
    ): URI? {
      val hasName = nodeHasName(e, ATOM_URI, "link")
      Preconditions.checkArgument(hasName, "Node has name 'link'")

      val hasEverything = e.hasAttribute("rel") && e.hasAttribute("href")
      if (hasEverything) {
        val r = Objects.requireNonNull(e.getAttribute("rel"))
        val h = Objects.requireNonNull(e.getAttribute("href"))

        if ("privacy-policy" == r) {
          try {
            return scrubURI(source, h)
          } catch (ex: URISyntaxException) {
            builder.addParseError(
              invalidURI(
                source,
                hrefAttributeOfLinkRel("privacy-policy"),
                ex
              )
            )
            return null
          }
        }
      }
      return null
    }

    private fun parseAuthenticationDocumentLink(
      source: URI,
      builder: OPDSAcquisitionFeedBuilderType,
      e: Element
    ): URI? {
      val hasName = nodeHasName(e, ATOM_URI, "link")

      Preconditions.checkArgument(hasName, "Node has name 'link'")

      val hasEverything = e.hasAttribute("rel") && e.hasAttribute("href")
      if (hasEverything) {
        val r: String = e.getAttribute("rel")
        val h: String = e.getAttribute("href")

        if (AUTHENTICATION_DOCUMENT_RELATION_URI_TEXT == r) {
          try {
            return scrubURI(source, h)
          } catch (ex: URISyntaxException) {
            builder.addParseError(
              invalidURI(
                source,
                hrefAttributeOfLinkRel(AUTHENTICATION_DOCUMENT_RELATION_URI_TEXT),
                ex
              )
            )
            return null
          }
        }
      }

      return null
    }

    private fun parseAnnotationsLink(
      source: URI,
      builder: OPDSAcquisitionFeedBuilderType,
      e: Element
    ): URI? {
      val hasName = nodeHasName(e, ATOM_URI, "link")
      Preconditions.checkArgument(hasName, "Node has name 'link'")

      val hasEverything = e.hasAttribute("rel") && e.hasAttribute("href")
      if (hasEverything) {
        val r: String = e.getAttribute("rel")
        val h: String = e.getAttribute("href")

        if ("http://www.w3.org/ns/oa#annotationService" == r) {
          try {
            return scrubURI(source, h)
          } catch (ex: URISyntaxException) {
            builder.addParseError(
              invalidURI(
                source,
                hrefAttributeOfLinkRel("http://www.w3.org/ns/oa#annotationService"),
                ex
              )
            )
            return null
          }
        }
      }

      return null
    }

    private fun hrefAttributeOfLinkRel(relValue: String): String {
      return "'href' attribute of 'link' with 'rel' $relValue"
    }

    private fun invalidURI(
      source: URI,
      sourceLocation: String,
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
  }
}
