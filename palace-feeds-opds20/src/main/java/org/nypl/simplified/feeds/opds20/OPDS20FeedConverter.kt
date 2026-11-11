package org.nypl.simplified.feeds.opds20

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import one.irradia.mime.api.MIMEType
import one.irradia.mime.vanilla.MIMEParser.Companion.parseRaisingException
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
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
import org.nypl.simplified.feeds.api.Feed as PalaceFeed

/**
 * Functions to parse OPDS 2.0 JSON using the Palace WebPub parser and
 * convert the result into the generic [PalaceFeed] types from [palace-feeds-api].
 */
object OPDS20FeedConverter {
  private const val REL_ACQUIRE_PREFIX = "/acquire/"
  private const val REL_ACQUIRE_SAMPLE = "/acquire/sample"
  private const val REL_ACQUIRE_OPEN_ACCESS = "/acquire/open-access"
  private const val REL_ACQUIRE_BUY = "/acquire/buy"
  private const val REL_ACQUIRE_SUBSCRIBE = "/acquire/subscribe"

  private const val REL_SELF = "self"
  private const val REL_NEXT = "next"
  private const val REL_SEARCH = "search"
  private const val REL_COVER = "cover"
  private const val REL_THUMBNAIL = "thumbnail"
  private const val REL_ALTERNATE = "alternate"
  private const val REL_RELATED = "related"
  private const val REL_ISSUES = "issues"
  private const val REL_COLLECTION = "collection"
  private const val REL_ABOUT = "about"
  private const val REL_TERMS_OF_SERVICE = "terms-of-service"
  private const val REL_PRIVACY_POLICY = "privacy-policy"
  private const val REL_LICENSES = "licenses"

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
  fun parse(stream: InputStream): WPMFeed = stream.use { mapper.readValue(it, WPMFeed::class.java) }

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
    val feedUpdated: DateTime =
      feed.metadata.modified?.let { offsetToDateTime(it) }
        ?: DateTime(0, DateTimeZone.UTC)

    val feedTitle: String =
      languageMapToString(feed.metadata.title)

    val facets: List<FeedFacetSingle> =
      feed.facets.map { convertFacet(accountID, it) }

    val facetsByGroup: Map<String, List<FeedFacetSingle>> =
      facets.groupBy { (it as FeedFacetData).group }

    val entries: List<FeedEntryBook> =
      feed.publications.map { publication ->
        convertEntry(accountID, publication)
      }

    val feedGroups: List<FeedGroup> =
      feed.groups.map { group ->
        val groupURI: URI =
          group.metadata.identifier
            ?: group.navigation
              .firstOrNull { REL_SELF in it.relation }
              ?.let { (it as? WPMLinkBasic)?.href }
            ?: feedURI

        val groupTitle: String =
          languageMapToString(group.metadata.title)

        val groupEntries: List<FeedEntryBook> =
          entries.filter { entry ->
            entry.groups.any { ref -> ref.uri == groupURI }
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

    val id: String =
      metadata.identifier?.toString() ?: ""

    val title: String =
      languageMapToString(metadata.title)

    val updated: DateTime =
      metadata.modified?.let { offsetToDateTime(it) }
        ?: DateTime(0, DateTimeZone.UTC)

    val published: DateTime? =
      metadata.published?.let { offsetToDateTime(it) }

    val authors: List<String> =
      metadata.author.map { contributorToString(it) }.filter { it.isNotEmpty() }

    val narrators: List<String> =
      metadata.narrator.map { contributorToString(it) }.filter { it.isNotEmpty() }

    val publisher: String? =
      metadata.publisher.firstOrNull()?.let { contributorToString(it) }

    val language: String? =
      metadata.languages.firstOrNull()

    val summary: String =
      metadata.description ?: ""

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
      links.filter { it.relation.any { rel -> REL_ACQUIRE_PREFIX in rel } }

    val previewAcquisitionLinks: List<WPMLink> =
      allAcquisitionLinks.filter { it.relation.any { it.endsWith(REL_ACQUIRE_SAMPLE) } }

    val acquisitionLinks: List<WPMLink> =
      allAcquisitionLinks.filterNot { it in previewAcquisitionLinks }

    val acquisitions: List<FeedAcquisition> =
      acquisitionLinks.map { link ->
        convertAcquisitionLink(link)
      }

    val previewAcquisitions: List<FeedPreviewAcquisition> =
      previewAcquisitionLinks.map { link ->
        val href: URI = linkHrefValue(link) ?: URI("")
        val type: MIMEType =
          link.type ?: parseRaisingException("application/octet-stream")
        FeedPreviewAcquisition(href, type)
      }

    val cover: URI? =
      linkHref(links, REL_COVER)

    val thumbnail: URI? =
      linkHref(links, REL_THUMBNAIL)

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
          val uri = linkHrefValue(link) ?: return@mapNotNull null
          val title = link.title
          FeedEntryGroupRef(name = title ?: "", uri = uri)
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
  ): FeedFacetData {
    val uri: URI =
      facet.links
        .firstOrNull { REL_SELF in it.relation }
        ?.let { linkHrefValue(it) }
        ?: URI("")

    val title: String =
      languageMapToString(facet.metadata.title)

    return FeedFacetData(
      accountID = accountID,
      title = title,
      isActive = true,
      uri = uri,
      group = "",
      groupType = null,
    )
  }

  private fun convertSubject(subject: WPMSubjectOrString): FeedCategory =
    when (subject) {
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

  private fun convertSeries(series: WPMSeries): FeedSeries? =
    when (series) {
      is WPMSeries.WPMSeriesString -> {
        FeedSeries(
          name = series.value,
          position = null,
          link = URI(""),
        )
      }

      is WPMSeries.WPMSeriesObject -> {
        FeedSeries(
          name = languageMapToString(series.name),
          position = series.position?.toInt(),
          link = series.identifier ?: URI(""),
        )
      }

      is WPMSeries.WPMSeriesSet -> {
        series.value.firstOrNull()?.let { convertSeries(it) }
      }
    }

  private fun convertAcquisitionLink(link: WPMLink): FeedAcquisition {
    val href: URI =
      linkHrefValue(link) ?: URI("")

    val mediaType: MIMEType =
      link.type ?: parseRaisingException("application/octet-stream")

    val indirectAcquisitions: List<FeedIndirectAcquisition> =
      link.properties
        ?.indirectAcquisition
        ?.map { convertIndirectAcquisitionTree(it) }
        ?: emptyList()

    val properties: Map<String, String> = emptyMap()

    return FeedAcquisition(
      relation = inferRelation(link.relation),
      uri = Links.wpmLinkToPalaceLink(link).toBasic(),
      type = mediaType,
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

  private fun inferRelation(rels: List<String>): FeedAcquisitionRelation =
    when {
      rels.any { it.endsWith(REL_ACQUIRE_OPEN_ACCESS) } -> FeedAcquisitionRelation.ACQUISITION_OPEN_ACCESS
      rels.any { it.endsWith(REL_ACQUIRE_SAMPLE) } -> FeedAcquisitionRelation.ACQUISITION_SAMPLE
      rels.any { it.endsWith(REL_ACQUIRE_BUY) } -> FeedAcquisitionRelation.ACQUISITION_BUY
      rels.any { it.endsWith(REL_ACQUIRE_SUBSCRIBE) } -> FeedAcquisitionRelation.ACQUISITION_SUBSCRIBE
      else -> FeedAcquisitionRelation.ACQUISITION_BORROW
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
          startDate = availability.since?.let { offsetToDateTime(it) },
          position = null,
          endDate = availability.until?.let { offsetToDateTime(it) },
          revoke = null,
        )
      }

      "ready" -> {
        FeedAvailabilityHeldReady(
          endDate = availability.until?.let { offsetToDateTime(it) },
          revoke = null,
        )
      }

      null -> {
        val hasOpenAccess =
          links.any { it.relation.any { rel -> REL_ACQUIRE_PREFIX in rel && rel.endsWith(REL_ACQUIRE_OPEN_ACCESS) } }
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
      val props = link.properties ?: continue
      val ext = props.extensionProperties

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
  private fun linkHrefValue(link: WPMLink): URI? =
    when (link) {
      is WPMLinkBasic -> link.href
      is WPMLinkTemplated -> null
    }

  /**
   * Find the first link with the given rel and return its href as a [URI].
   */
  private fun linkHref(
    links: List<WPMLink>,
    rel: String,
  ): URI? =
    links
      .firstOrNull { rel in it.relation }
      ?.let { linkHrefValue(it) }

  /**
   * Convert a [WPMLanguageMap] to a String.
   */
  private fun languageMapToString(map: WPMLanguageMap): String =
    when (map) {
      is WPMLanguageMap.Scalar -> map.value
      is WPMLanguageMap.Mapped -> map.defaultValue
    }

  /**
   * Convert a [WPMContributorOrString] to a String.
   */
  private fun contributorToString(contributor: WPMContributorOrString): String =
    when (contributor) {
      is WPMContributorOrString.WPMContributorString -> contributor.value
      is WPMContributor -> contributor.name ?: ""
    }

  /**
   * Convert a java.time [java.time.OffsetDateTime] to a Joda [DateTime] in UTC.
   */
  private fun offsetToDateTime(offset: java.time.OffsetDateTime): DateTime =
    DateTime(offset.toInstant().toEpochMilli(), DateTimeZone.UTC)
}
