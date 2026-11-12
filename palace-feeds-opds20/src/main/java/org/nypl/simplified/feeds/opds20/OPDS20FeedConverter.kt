package org.nypl.simplified.feeds.opds20

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import one.irradia.mime.api.MIMEType
import one.irradia.mime.vanilla.MIMEParser.Companion.parseRaisingException
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedAcquisition
import org.nypl.simplified.feeds.api.FeedAcquisitionRelation
import org.nypl.simplified.feeds.api.FeedAvailability
import org.nypl.simplified.feeds.api.FeedAvailabilityHeld
import org.nypl.simplified.feeds.api.FeedAvailabilityHeldReady
import org.nypl.simplified.feeds.api.FeedAvailabilityHoldable
import org.nypl.simplified.feeds.api.FeedAvailabilityLoanable
import org.nypl.simplified.feeds.api.FeedAvailabilityOpenAccess
import org.nypl.simplified.feeds.api.FeedCategory
import org.nypl.simplified.feeds.api.FeedDRMLicensor
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.feeds.api.FeedDataWithGroups
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedEntryGroupRef
import org.nypl.simplified.feeds.api.FeedFacetData
import org.nypl.simplified.feeds.api.FeedFacetSingle
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.feeds.api.FeedIndirectAcquisition
import org.nypl.simplified.feeds.api.FeedPreviewAcquisition
import org.nypl.simplified.feeds.api.FeedSearchOpen1_1
import org.nypl.simplified.feeds.api.FeedSeries
import org.nypl.simplified.links.Links
import org.thepalaceproject.webpub.core.WPMContributor
import org.thepalaceproject.webpub.core.WPMContributorOrString
import org.thepalaceproject.webpub.core.WPMFacet
import org.thepalaceproject.webpub.core.WPMFeed
import org.thepalaceproject.webpub.core.WPMIndirectAcquisitionTree
import org.thepalaceproject.webpub.core.WPMLanguageMap
import org.thepalaceproject.webpub.core.WPMLink
import org.thepalaceproject.webpub.core.WPMLinkBasic
import org.thepalaceproject.webpub.core.WPMLinkTemplated
import org.thepalaceproject.webpub.core.WPMMappers
import org.thepalaceproject.webpub.core.WPMPublication
import org.thepalaceproject.webpub.core.WPMSeries
import org.thepalaceproject.webpub.core.WPMSubjectOrString
import java.io.InputStream
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.nypl.simplified.feeds.api.Feed as PalaceFeed

/**
 * Functions to parse OPDS 2.0 JSON using the Palace WebPub parser and
 * convert the result into the generic [PalaceFeed] types from [palace-feeds-api].
 */
object OPDS20FeedConverter {
  private const val REL_ACQUIRE = "http://opds-spec.org/acquisition"
  private const val REL_ACQUIRE_PREFIX = "http://opds-spec.org/acquisition/"
  private const val REL_ACQUIRE_BORROW = "http://opds-spec.org/acquisition/borrow"
  private const val REL_ACQUIRE_SAMPLE = "http://opds-spec.org/acquisition/sample"
  private const val REL_ACQUIRE_OPEN_ACCESS = "http://opds-spec.org/acquisition/open-access"
  private const val REL_ACQUIRE_BUY = "http://opds-spec.org/acquisition/buy"
  private const val REL_ACQUIRE_SUBSCRIBE = "http://opds-spec.org/acquisition/subscribe"

  private const val REL_SELF = "self"
  private const val REL_NEXT = "next"
  private const val REL_SEARCH = "search"
  private const val REL_COVER = "http://opds-spec.org/image"
  private const val REL_THUMBNAIL = "http://opds-spec.org/image/thumbnail"
  private const val REL_ALTERNATE = "alternate"
  private const val REL_RELATED = "related"
  private const val REL_ISSUES = "issues"
  private const val REL_COLLECTION = "collection"
  private const val REL_ABOUT = "about"
  private const val REL_TERMS_OF_SERVICE = "terms-of-service"
  private const val REL_PRIVACY_POLICY = "privacy-policy"
  private const val REL_LICENSES = "license"
  private const val REL_AUTH_DOCUMENT = "http://opds-spec.org/auth/document"

  private const val REL_ANNOTATION_SERVICE = "http://www.w3.org/ns/oa#annotationService"
  private const val REL_TIME_TRACKING = "http://palaceproject.io/terms/timeTracking"
  private const val REL_ANALYTICS_OPEN_BOOK = "http://librarysimplified.org/terms/rel/analytics/open-book"

  private const val EXT_PROP_LICENSOR = "licensor"
  private const val EXT_PROP_VENDOR = "vendor"
  private const val EXT_PROP_CLIENT_TOKEN = "clientToken"
  private const val EXT_PROP_DEVICE_MANAGER = "deviceManager"

  private val mapper: ObjectMapper =
    WPMMappers
      .createMapper()
      .copy()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

  /**
   * Parse OPDS 2.0 JSON from the given stream into a [WPMFeed].
   */
  fun parse(stream: InputStream): WPMFeed {
    return stream.use { this.mapper.readValue(it, WPMFeed::class.java) }
  }

  /**
   * Parse OPDS 2.0 JSON from the given stream into a [WPMPublication].
   */
  fun parsePublication(stream: InputStream): WPMPublication {
    return stream.use { this.mapper.readValue(it, WPMPublication::class.java) }
  }

  /**
   * Convert a parsed [WPMFeed] into a [PalaceFeed].
   *
   * @param accountID The account that owns the feed
   * @param feed The parsed OPDS 2.0 feed
   * @param feedURI The URI of the feed
   * @return The converted feed
   */
  fun convert(
    accountID: AccountID,
    feed: WPMFeed,
    feedURI: URI,
  ): PalaceFeed {
    val feedUpdated: OffsetDateTime =
      orEpoch(feed.metadata.modified)

    val feedTitle: String =
      languageMapToString(feed.metadata.title)

    val facets: List<FeedFacetSingle> =
      feed.facets.flatMap { convertFacet(accountID, it) }

    val facetsByGroup: Map<String, List<FeedFacetSingle>> =
      facets.groupBy { (it as FeedFacetData).group }

    val entries: List<FeedEntryBook> =
      feed.publications.map { publication ->
        convertEntry(accountID, publication)
      }

    val feedGroups: List<FeedGroup> =
      feed.groups.map { group ->
        val groupURI: URI
        val groupIdentifier = group.metadata.identifier
        if (groupIdentifier != null) {
          groupURI = groupIdentifier
        } else {
          val selfLink = group.links.firstOrNull { REL_SELF in it.relation }
          if (selfLink != null) {
            val basicLink = selfLink as? WPMLinkBasic
            if (basicLink != null) {
              groupURI = basicLink.href
            } else {
              groupURI = feedURI
            }
          } else {
            groupURI = feedURI
          }
        }

        val groupTitle: String =
          languageMapToString(group.metadata.title)

        val groupEntries: List<FeedEntryBook> =
          group.publications.map { publication ->
            convertEntry(accountID, publication)
          }

        FeedGroup(
          account = accountID,
          groupTitle = groupTitle,
          groupURI = groupURI,
          groupEntries = groupEntries,
        )
      }

    val feedNext: URI? =
      linkHref(feed.links, REL_NEXT)

    val feedSearch: FeedSearchOpen1_1? =
      feed.links
        .firstOrNull { REL_SEARCH in it.relation }
        ?.let { link ->
          val href: String =
            when (link) {
              is WPMLinkBasic -> link.href.toString()
              is WPMLinkTemplated -> link.href
            }
          if (href.contains("{searchTerms}")) {
            FeedSearchOpen1_1.fromTemplate(href)
          } else {
            null
          }
        }

    val feedTermsOfService: URI? =
      linkHref(feed.links, REL_TERMS_OF_SERVICE)

    val feedPrivacyPolicy: URI? =
      linkHref(feed.links, REL_PRIVACY_POLICY)

    val feedAbout: URI? =
      linkHref(feed.links, REL_ABOUT)

    val feedLicenses: URI? =
      linkHref(feed.links, REL_LICENSES)

    val feedAuthDocument: URI? =
      linkHref(feed.links, REL_AUTH_DOCUMENT)

    return if (feedGroups.isEmpty()) {
      FeedData(
        feedID = feedURI.toString(),
        feedSearch = feedSearch,
        feedTitle = feedTitle,
        feedUpdated = feedUpdated,
        feedURI = feedURI,
        feedTermsOfService = feedTermsOfService,
        feedPrivacyPolicy = feedPrivacyPolicy,
        feedAbout = feedAbout,
        feedLicenses = feedLicenses,
        feedNext = feedNext,
        feedAuthDocument = feedAuthDocument,
        facetsByGroup = facetsByGroup,
        facetsOrder = facets,
        entries = entries,
      )
    } else {
      FeedDataWithGroups(
        feedID = feedURI.toString(),
        feedSearch = feedSearch,
        feedTitle = feedTitle,
        feedUpdated = feedUpdated,
        feedURI = feedURI,
        feedTermsOfService = feedTermsOfService,
        feedPrivacyPolicy = feedPrivacyPolicy,
        feedAbout = feedAbout,
        feedLicenses = feedLicenses,
        feedNext = feedNext,
        feedAuthDocument = feedAuthDocument,
        facetsByGroup = facetsByGroup,
        facetsOrder = facets,
        groups = feedGroups,
      )
    }
  }

  /**
   * Convert a WebPub [WPMPublication] into a [FeedEntryBook].
   */
  fun convertEntry(
    accountID: AccountID,
    publication: WPMPublication,
  ): FeedEntryBook {
    val metadata = publication.metadata
    val links = publication.links

    val id: String
    val entryIdentifier = metadata.identifier
    if (entryIdentifier == null) {
      id = ""
    } else {
      id = entryIdentifier.toString()
    }

    val title: String =
      languageMapToString(metadata.title)

    val updated: OffsetDateTime =
      orEpoch(metadata.modified)

    val published: OffsetDateTime? =
      metadata.published

    val authors: List<String> =
      metadata.author.map { contributorToString(it) }.filter { it.isNotEmpty() }

    val narrators: List<String> =
      metadata.narrator.map { contributorToString(it) }.filter { it.isNotEmpty() }

    val publisher: String? =
      metadata.publisher.firstOrNull()?.let { contributorToString(it) }

    val language: String? =
      metadata.languages.firstOrNull()

    val summary: String
    val entryDescription = metadata.description
    if (entryDescription == null) {
      summary = ""
    } else {
      summary = entryDescription
    }

    val duration: Double? =
      metadata.duration?.toDouble()

    val categories: List<FeedCategory> =
      metadata.subjects.map { convertSubject(it) }

    val audience: String? =
      null

    val series: FeedSeries? =
      metadata.belongsTo?.series?.let { convertSeries(it) }

    /*
     * Separate acquisitions from preview acquisitions.
     */
    val allAcquisitionLinks: List<WPMLink> =
      links.filter { it.relation.any { rel -> rel == REL_ACQUIRE || rel.startsWith(REL_ACQUIRE_PREFIX) } }

    val previewAcquisitionLinks: List<WPMLink> =
      allAcquisitionLinks.filter { it.relation.any { it.endsWith(REL_ACQUIRE_SAMPLE) } }

    val acquisitionLinks: List<WPMLink> =
      allAcquisitionLinks.filterNot { it in previewAcquisitionLinks }

    val acquisitions: List<FeedAcquisition> =
      acquisitionLinks.map { link ->
        convertAcquisitionLink(link)
      }

    val previewAcquisitions: List<FeedPreviewAcquisition> =
      previewAcquisitionLinks.mapNotNull { link ->
        val hrefValue = linkHrefValue(link)
        if (hrefValue == null) {
          // a preview acquired from a templated link has no concrete URI and is unusable
          null
        } else {
          val rawType: MIMEType?
          val linkType = link.type
          if (linkType == null) {
            rawType = parseRaisingException("application/octet-stream")
          } else {
            rawType = linkType
          }

          FeedPreviewAcquisition(hrefValue, rawType)
        }
      }

    val allLinks: List<WPMLink> =
      links + publication.images

    val cover: URI? =
      linkHref(allLinks, REL_COVER)

    val thumbnail: URI? =
      linkHref(allLinks, REL_THUMBNAIL)

    val annotations: URI? =
      linkHref(links, REL_ANNOTATION_SERVICE)

    val issues: URI? =
      linkHref(links, REL_ISSUES)

    val related: URI? =
      linkHref(links, REL_RELATED)

    val alternate: URI? =
      linkHref(links, REL_ALTERNATE)

    val analytics: URI? =
      linkHref(links, REL_ANALYTICS_OPEN_BOOK)

    val timeTrackingUri: URI? =
      linkHref(links, REL_TIME_TRACKING)

    val licensor: FeedDRMLicensor? =
      extractDRMLicensor(links)

    val entryGroups: Set<FeedEntryGroupRef> =
      links
        .filter { REL_COLLECTION in it.relation }
        .mapNotNull { link ->
          val uri = linkHrefValue(link)
          val linkTitle = link.title
          if (uri == null || linkTitle == null || linkTitle.isEmpty()) {
            // a group without a name or without a URI is not a valid group
            null
          } else {
            FeedEntryGroupRef(name = linkTitle, uri = uri)
          }
        }.toSet()

    val availability: FeedAvailability =
      convertAvailability(links)

    return FeedEntryBook(
      accountID = accountID,
      id = id,
      title = title,
      updated = updated,
      authors = authors,
      availability = availability,
      acquisitions = acquisitions,
      categories = categories,
      cover = cover,
      previewAcquisitions = previewAcquisitions,
      annotations = annotations,
      groups = entryGroups,
      issues = issues,
      related = related,
      published = published,
      publisher = publisher,
      distribution = "",
      summary = summary,
      narrators = narrators,
      thumbnail = thumbnail,
      timeTrackingUri = timeTrackingUri,
      alternate = alternate,
      analytics = analytics,
      licensor = licensor,
      duration = duration,
      language = language,
      audience = audience,
      series = series,
    )
  }

  private fun convertFacet(
    accountID: AccountID,
    facet: WPMFacet,
  ): List<FeedFacetData> {
    val groupName: String =
      languageMapToString(facet.metadata.title)

    val result = mutableListOf<FeedFacetData>()

    for (link in facet.links) {
      val linkTitle: String
      val rawTitle = link.title
      if (rawTitle == null || rawTitle.isEmpty()) {
        linkTitle = groupName
      } else {
        linkTitle = rawTitle
      }

      val linkURI: URI? = linkHrefValue(link)

      val isActive: Boolean =
        REL_SELF in link.relation

      result.add(
        FeedFacetData(
          accountID = accountID,
          title = linkTitle,
          isActive = isActive,
          uri = linkURI,
          group = groupName,
          groupType = null,
        ),
      )
    }

    return result
  }

  private fun convertSubject(subject: WPMSubjectOrString): FeedCategory {
    return when (subject) {
      is WPMSubjectOrString.WPMSubjectString -> {
        FeedCategory(
          term = subject.value,
          scheme = "",
          label = subject.value,
        )
      }

      else -> {
        FeedCategory(
          term = "",
          scheme = "",
          label = "",
        )
      }
    }
  }

  private fun convertSeries(series: WPMSeries): FeedSeries? {
    return when (series) {
      is WPMSeries.WPMSeriesString -> {
        FeedSeries(
          name = series.value,
          position = null,
          link = null,
        )
      }

      is WPMSeries.WPMSeriesObject -> {
        val seriesPosition: Int?
        val rawPosition = series.position
        if (rawPosition == null) {
          seriesPosition = null
        } else {
          seriesPosition = rawPosition.toInt()
        }

        FeedSeries(
          name = languageMapToString(series.name),
          position = seriesPosition,
          link = series.identifier,
        )
      }

      is WPMSeries.WPMSeriesSet -> {
        val firstElement = series.value.firstOrNull()
        if (firstElement == null) {
          null
        } else {
          convertSeries(firstElement)
        }
      }
    }
  }

  private fun convertAcquisitionLink(link: WPMLink): FeedAcquisition {
    val rawType: MIMEType?
    val linkType = link.type
    if (linkType == null) {
      rawType = parseRaisingException("application/octet-stream")
    } else {
      rawType = linkType
    }

    val indirectAcquisitions: List<FeedIndirectAcquisition>
    val linkProperties = link.properties
    if (linkProperties == null) {
      indirectAcquisitions = emptyList()
    } else {
      val indirectTrees = linkProperties.indirectAcquisition
      if (indirectTrees == null) {
        indirectAcquisitions = emptyList()
      } else {
        indirectAcquisitions = indirectTrees.map { convertIndirectAcquisitionTree(it) }
      }
    }

    val properties: Map<String, String> = emptyMap()

    return FeedAcquisition(
      relation = inferRelation(link.relation),
      uri = Links.wpmLinkToPalaceLink(link).toBasic(),
      type = rawType,
      indirectAcquisitions = indirectAcquisitions,
      properties = properties,
    )
  }

  private fun convertIndirectAcquisitionTree(tree: WPMIndirectAcquisitionTree): FeedIndirectAcquisition {
    val type: MIMEType =
      parseRaisingException(tree.type)

    val children: List<FeedIndirectAcquisition> =
      tree.children.map { convertIndirectAcquisitionTree(it) }

    return FeedIndirectAcquisition(
      type = type,
      indirectAcquisitions = children,
      properties = emptyMap(),
    )
  }

  /**
   * Map the relation of an acquisition link to a [FeedAcquisitionRelation].
   * This mirrors the OPDS 1.2 parser: the bare `http://opds-spec.org/acquisition`
   * relation is `ACQUISITION_GENERIC`, and each suffixed relation maps to its
   * corresponding type. Any unrecognised relation is treated as generic.
   */
  private fun inferRelation(rels: List<String>): FeedAcquisitionRelation {
    for (rel in rels) {
      when (rel) {
        REL_ACQUIRE -> return FeedAcquisitionRelation.ACQUISITION_GENERIC
        REL_ACQUIRE_BORROW -> return FeedAcquisitionRelation.ACQUISITION_BORROW
        REL_ACQUIRE_BUY -> return FeedAcquisitionRelation.ACQUISITION_BUY
        REL_ACQUIRE_OPEN_ACCESS -> return FeedAcquisitionRelation.ACQUISITION_OPEN_ACCESS
        REL_ACQUIRE_SAMPLE -> return FeedAcquisitionRelation.ACQUISITION_SAMPLE
        REL_ACQUIRE_SUBSCRIBE -> return FeedAcquisitionRelation.ACQUISITION_SUBSCRIBE
      }
    }
    return FeedAcquisitionRelation.ACQUISITION_GENERIC
  }

  private fun convertAvailability(links: List<WPMLink>): FeedAvailability {
    val availability =
      links
        .mapNotNull { it.properties?.availability }
        .firstOrNull()

    return when (availability?.state) {
      "available" -> {
        FeedAvailabilityLoanable
      }

      "unavailable" -> {
        FeedAvailabilityHoldable
      }

      "reserved" -> {
        FeedAvailabilityHeld(
          startDate = availability.since,
          position = null,
          endDate = availability.until,
          revoke = null,
        )
      }

      "ready" -> {
        FeedAvailabilityHeldReady(
          endDate = availability.until,
          revoke = null,
        )
      }

      null -> {
        val hasOpenAccess =
          links.any { it.relation.any { rel -> rel == REL_ACQUIRE_OPEN_ACCESS } }
        if (hasOpenAccess) {
          FeedAvailabilityOpenAccess(endDate = null, revoke = null)
        } else {
          FeedAvailabilityLoanable
        }
      }

      else -> {
        FeedAvailabilityLoanable
      }
    }
  }

  /**
   * Extract DRM licensor information from the link extension properties.
   */
  private fun extractDRMLicensor(links: List<WPMLink>): FeedDRMLicensor? {
    for (link in links) {
      val linkProperties = link.properties
      if (linkProperties == null) {
        continue
      }
      val ext = linkProperties.extensionProperties

      val licensorNode = ext[EXT_PROP_LICENSOR]
      if (licensorNode != null && licensorNode.isObject) {
        val vendor = licensorNode.get(EXT_PROP_VENDOR)?.asText()
        val clientToken = licensorNode.get(EXT_PROP_CLIENT_TOKEN)?.asText()
        val deviceManager = licensorNode.get(EXT_PROP_DEVICE_MANAGER)?.asText()

        if (vendor != null && clientToken != null) {
          return FeedDRMLicensor(
            vendor = vendor,
            clientToken = clientToken,
            deviceManager = deviceManager,
          )
        }
      }

      // Check for DRM-related rels with vendor/clientToken in extension properties
      val hasDrmRel =
        link.relation.any { rel ->
          rel.contains("drm", ignoreCase = true) ||
            rel.contains("lcp", ignoreCase = true) ||
            rel == "http://librarysimplified.org/terms/drm/rel/devices"
        }

      if (hasDrmRel) {
        val vendor = ext[EXT_PROP_VENDOR]?.asText()
        val clientToken = ext[EXT_PROP_CLIENT_TOKEN]?.asText()
        val deviceManager = ext[EXT_PROP_DEVICE_MANAGER]?.asText()

        if (vendor != null && clientToken != null) {
          return FeedDRMLicensor(
            vendor = vendor,
            clientToken = clientToken,
            deviceManager = deviceManager,
          )
        }
      }
    }

    return null
  }

  /**
   * Get the href URI value from a WebPub link.
   */
  private fun linkHrefValue(link: WPMLink): URI? {
    return when (link) {
      is WPMLinkBasic -> link.href
      is WPMLinkTemplated -> null
    }
  }

  /**
   * Find the first link with the given rel and return its href as a [URI].
   */
  private fun linkHref(
    links: List<WPMLink>,
    rel: String,
  ): URI? {
    val firstLink = links.firstOrNull { rel in it.relation }
    if (firstLink == null) {
      return null
    }
    return linkHrefValue(firstLink)
  }

  /**
   * Convert a [WPMLanguageMap] to a String.
   */
  private fun languageMapToString(map: WPMLanguageMap): String {
    return when (map) {
      is WPMLanguageMap.Scalar -> map.value
      is WPMLanguageMap.Mapped -> map.defaultValue
    }
  }

  /**
   * Convert a [WPMContributorOrString] to a String.
   */
  private fun contributorToString(contributor: WPMContributorOrString): String {
    return when (contributor) {
      is WPMContributorOrString.WPMContributorString -> {
        contributor.value
      }

      is WPMContributor -> {
        val name = contributor.name
        if (name == null) {
          ""
        } else {
          name
        }
      }
    }
  }

  /**
   * Return [offset], or the epoch instant in UTC if it is null.
   */
  private fun orEpoch(offset: OffsetDateTime?): OffsetDateTime {
    if (offset == null) {
      return Instant.EPOCH.atOffset(ZoneOffset.UTC)
    }
    return offset
  }
}
