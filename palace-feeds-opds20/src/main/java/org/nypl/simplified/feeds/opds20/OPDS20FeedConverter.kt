package org.nypl.simplified.feeds.opds20

import java.net.URI
import kotlin.time.Instant
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.Feed as PalaceFeed
import org.nypl.simplified.feeds.api.FeedAcquisition
import org.nypl.simplified.feeds.api.FeedAcquisitionRelation
import org.nypl.simplified.feeds.api.FeedAvailability
import org.nypl.simplified.feeds.api.FeedAvailabilityHeld
import org.nypl.simplified.feeds.api.FeedAvailabilityHeldReady
import org.nypl.simplified.feeds.api.FeedAvailabilityHoldable
import org.nypl.simplified.feeds.api.FeedAvailabilityLoanable
import org.nypl.simplified.feeds.api.FeedAvailabilityOpenAccess
import org.nypl.simplified.feeds.api.FeedCategory
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.feeds.api.FeedDataWithGroups
import org.nypl.simplified.feeds.api.FeedDRMLicensor
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedEntryGroupRef
import org.nypl.simplified.feeds.api.FeedFacetData
import org.nypl.simplified.feeds.api.FeedFacetSingle
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.feeds.api.FeedIndirectAcquisition
import org.nypl.simplified.feeds.api.FeedPreviewAcquisition
import org.nypl.simplified.feeds.api.FeedSearchOpen1_1
import org.nypl.simplified.feeds.api.FeedSeries
import org.nypl.simplified.links.Link.LinkBasic
import one.irradia.mime.api.MIMEType
import one.irradia.mime.vanilla.MIMEParser.Companion.parseRaisingException
import org.readium.r2.shared.opds.Acquisition
import org.readium.r2.shared.opds.Availability
import org.readium.r2.shared.opds.Facet
import org.readium.r2.shared.opds.Feed as ReadiumFeed
import org.readium.r2.shared.publication.Link as ReadiumLink
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Subject
import org.readium.r2.shared.publication.opds.availability
import org.readium.r2.shared.publication.opds.indirectAcquisitions
import org.readium.r2.shared.util.Url

/**
 * Functions to convert Readium OPDS 2.0 types into the
 * generic [PalaceFeed] types from [palace-feeds-api].
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
  private const val REL_REVOKE = "http://librarysimplified.org/terms/rel/revoke"
  private const val REL_DRM_DEVICES = "http://librarysimplified.org/terms/drm/rel/devices"
  private const val SCHEME_AUDIENCE = "http://schema.org/audience"

  /**
   * Convert a Readium OPDS 2.0 [ReadiumFeed] into a [PalaceFeed].
   *
   * @param accountID The account that owns the feed
   * @param feed The parsed OPDS 2.0 feed
   * @return The converted feed
   */
  fun convert(
    accountID: AccountID,
    feed: ReadiumFeed
  ): PalaceFeed {
    val feedURI: URI =
      URI(feed.href.toString())

    val feedUpdated: DateTime =
      feed.metadata.modified?.let { instantToDateTime(it) }
        ?: DateTime(0, DateTimeZone.UTC)

    val facets: List<FeedFacetSingle> =
      feed.facets.map { convertFacet(accountID, it) }

    val facetsByGroup: Map<String, List<FeedFacetSingle>> =
      facets.groupBy { (it as FeedFacetData).group }
        .mapValues { it.value }

    val entries: List<FeedEntry> =
      feed.publications.map { convertEntry(accountID, it) }

    val feedGroups: List<FeedGroup> =
      feed.groups.map { group ->
        val groupURI: URI =
          group.links
            .firstOrNull { REL_SELF in it.rels }
            ?.href
            ?.toString()
            ?.let { URI(it) }
            ?: feedURI

        FeedGroup(
          account = accountID,
          groupTitle = group.title,
          groupURI = groupURI,
          groupEntries = group.publications.map { convertEntry(accountID, it) }
        )
      }

    val feedNext: URI? =
      feed.links
        .firstOrNull { REL_NEXT in it.rels }
        ?.href
        ?.toString()
        ?.let { URI(it) }

    val feedSearch: FeedSearchOpen1_1? =
      feed.links
        .firstOrNull { REL_SEARCH in it.rels }
        ?.let { link ->
          try {
            FeedSearchOpen1_1.fromTemplate(link.url(feed.href).toString())
          } catch (_: Exception) {
            FeedSearchOpen1_1.fromTemplate(link.href.toString())
          }
        }

    val feedTermsOfService: URI? =
      feedLinkURI(feed.links, REL_TERMS_OF_SERVICE, feed.href)

    val feedPrivacyPolicy: URI? =
      feedLinkURI(feed.links, REL_PRIVACY_POLICY, feed.href)

    val feedAbout: URI? =
      feedLinkURI(feed.links, REL_ABOUT, feed.href)

    val feedLicenses: URI? =
      feedLinkURI(feed.links, REL_LICENSES, feed.href)

    val feedTitle: String =
      feed.metadata.title

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
        entries = entries
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
        groups = feedGroups
      )
    }
  }

  /**
   * Convert a Readium [Publication] into a [FeedEntryBook].
   */
  fun convertEntry(accountID: AccountID, publication: Publication): FeedEntryBook {
    val metadata = publication.metadata
    val links = publication.links
    val baseUrl: Url? = publication.baseUrl

    val id: String =
      metadata.identifier ?: ""

    val title: String =
      metadata.localizedTitle?.string ?: ""

    val updated: DateTime =
      metadata.modified?.let { instantToDateTime(it) }
        ?: DateTime(0, DateTimeZone.UTC)

    val published: DateTime? =
      metadata.published?.let { instantToDateTime(it) }

    val authors: List<String> =
      metadata.authors.map { it.localizedName.string }.filter { it.isNotEmpty() }

    val narrators: List<String> =
      metadata.narrators.map { it.localizedName.string }.filter { it.isNotEmpty() }

    val publisher: String? =
      metadata.publishers.firstOrNull()?.let { it.localizedName.string }

    val language: String? =
      metadata.languages.firstOrNull()

    val summary: String =
      metadata.description ?: ""

    val duration: Double? =
      metadata.duration

    val categories: List<FeedCategory> =
      metadata.subjects.map { convertSubject(it) }

    val audience: String? =
      metadata.subjects
        .firstOrNull { it.scheme == SCHEME_AUDIENCE }
        ?.code

    val series: FeedSeries? =
      metadata.belongsTo
        ?.entries
        ?.firstOrNull()
        ?.let { (_, contributors) ->
          contributors.firstOrNull()?.let { contributor ->
            FeedSeries(
              name = contributor.localizedName.string,
              position = contributor.position?.toInt(),
              link = contributor.links.firstOrNull()?.href?.toString()
                ?.let { s -> URI(s) }
                ?: URI("")
            )
          }
        }

    /*
     * Separate acquisitions from preview acquisitions.
     * Preview acquisitions are those with the "sample" rel.
     */
    val allAcquisitionLinks: List<ReadiumLink> =
      links.filter { it.rels.any { rel -> REL_ACQUIRE_PREFIX in rel } }

    val previewAcquisitionLinks: List<ReadiumLink> =
      allAcquisitionLinks.filter { it.rels.any { it.endsWith(REL_ACQUIRE_SAMPLE) } }

    val acquisitionLinks: List<ReadiumLink> =
      allAcquisitionLinks.filterNot { it in previewAcquisitionLinks }

    val acquisitions: List<FeedAcquisition> =
      acquisitionLinks.map { convertAcquisitionLink(it, baseUrl) }

    val previewAcquisitions: List<FeedPreviewAcquisition> =
      previewAcquisitionLinks.map { link ->
        val href: String =
          linkResolvedHref(link, baseUrl)
        val type: MIMEType =
          link.mediaType?.let { parseRaisingException(it.toString()) }
            ?: parseRaisingException("application/octet-stream")
        FeedPreviewAcquisition(URI(href), type)
      }

    val cover: URI? =
      linkHrefAsURI(links, REL_COVER, baseUrl)

    val thumbnail: URI? =
      linkHrefAsURI(links, REL_THUMBNAIL, baseUrl)

    val annotations: URI? =
      linkHrefAsURI(links, REL_ANNOTATION_SERVICE, baseUrl)

    val issues: URI? =
      linkHrefAsURI(links, REL_ISSUES, baseUrl)

    val related: URI? =
      linkHrefAsURI(links, REL_RELATED, baseUrl)

    val alternate: URI? =
      linkHrefAsURI(links, REL_ALTERNATE, baseUrl)

    val analytics: URI? =
      linkHrefAsURI(links, REL_ANALYTICS_OPEN_BOOK, baseUrl)

    val timeTrackingUri: URI? =
      linkHrefAsURI(links, REL_TIME_TRACKING, baseUrl)

    val licensor: FeedDRMLicensor? =
      extractDRMLicensor(links)

    val entryGroups: Set<FeedEntryGroupRef> =
      links
        .filter { REL_COLLECTION in it.rels }
        .mapNotNull { link ->
          val title = link.title
          if (title != null) {
            val uri: URI = try {
              URI(link.url(baseUrl).toString())
            } catch (_: Exception) {
              URI(link.href.toString())
            }
            FeedEntryGroupRef(name = title, uri = uri)
          } else {
            null
          }
        }
        .toSet()

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
      series = series
    )
  }

  private fun convertFacet(accountID: AccountID, facet: Facet): FeedFacetData {
    val uri: URI =
      facet.links
        .firstOrNull { REL_SELF in it.rels }
        ?.href
        ?.toString()
        ?.let { URI(it) }
        ?: URI("")

    return FeedFacetData(
      accountID = accountID,
      title = facet.title,
      isActive = true,
      uri = uri,
      group = "",
      groupType = null
    )
  }

  private fun convertSubject(subject: Subject): FeedCategory =
    FeedCategory(
      term = subject.code ?: "",
      scheme = subject.scheme ?: "",
      label = subject.localizedName.string
    )

  private fun convertAcquisitionLink(link: ReadiumLink, baseUrl: Url?): FeedAcquisition {
    val href: String =
      linkResolvedHref(link, baseUrl)

    val mediaType: MIMEType =
      link.mediaType?.let { parseRaisingException(it.toString()) }
        ?: parseRaisingException("application/octet-stream")

    val indirectAcquisitions: List<FeedIndirectAcquisition> =
      link.properties.indirectAcquisitions
        .map { convertIndirectAcquisition(it) }

    val properties: Map<String, String> =
      link.properties.otherProperties
        .filterKeys { it !in RESERVED_PROPERTY_KEYS }
        .mapValues { (_, v) -> v.toString() }

    return FeedAcquisition(
      relation = inferRelation(link.rels),
      uri = LinkBasic(URI(href)),
      type = mediaType,
      indirectAcquisitions = indirectAcquisitions,
      properties = properties
    )
  }

  private val RESERVED_PROPERTY_KEYS: Set<String> =
    setOf("indirectAcquisition", "availability", "price", "holds", "copies", "numberOfItems", "authenticate")

  private fun convertIndirectAcquisition(acquisition: Acquisition): FeedIndirectAcquisition =
    FeedIndirectAcquisition(
      type = parseRaisingException(acquisition.type),
      indirectAcquisitions = acquisition.children.map { convertIndirectAcquisition(it) },
      properties = emptyMap()
    )

  private fun inferRelation(rels: Set<String>): FeedAcquisitionRelation =
    when {
      rels.any { it.endsWith(REL_ACQUIRE_OPEN_ACCESS) } -> FeedAcquisitionRelation.ACQUISITION_OPEN_ACCESS
      rels.any { it.endsWith(REL_ACQUIRE_SAMPLE) } -> FeedAcquisitionRelation.ACQUISITION_SAMPLE
      rels.any { it.endsWith(REL_ACQUIRE_BUY) } -> FeedAcquisitionRelation.ACQUISITION_BUY
      rels.any { it.endsWith(REL_ACQUIRE_SUBSCRIBE) } -> FeedAcquisitionRelation.ACQUISITION_SUBSCRIBE
      else -> FeedAcquisitionRelation.ACQUISITION_BORROW
    }

  private fun convertAvailability(links: List<ReadiumLink>): FeedAvailability {
    val availability: Availability? =
      links
        .mapNotNull { it.properties.availability }
        .firstOrNull()

    return when (availability?.state) {
      Availability.State.AVAILABLE -> {
        FeedAvailabilityLoanable
      }

      Availability.State.UNAVAILABLE -> {
        FeedAvailabilityHoldable
      }

      Availability.State.RESERVED -> {
        FeedAvailabilityHeld(
          startDate = availability.since?.let { instantToDateTime(it) },
          position = null,
          endDate = availability.until?.let { instantToDateTime(it) },
          revoke = null
        )
      }

      Availability.State.READY -> {
        FeedAvailabilityHeldReady(
          endDate = availability.until?.let { instantToDateTime(it) },
          revoke = null
        )
      }

      null -> {
        val hasOpenAccess =
          links.any { it.rels.any { rel -> REL_ACQUIRE_PREFIX in rel && rel.endsWith(REL_ACQUIRE_OPEN_ACCESS) } }
        if (hasOpenAccess) {
          FeedAvailabilityOpenAccess(endDate = null, revoke = null)
        } else {
          FeedAvailabilityLoanable
        }
      }
    }
  }

  /**
   * Extract DRM licensor information from the links.
   *
   * The Simplified profile conveys DRM information through a link with DRM-related rels
   * and properties containing the licensor details.
   */
  private fun extractDRMLicensor(links: List<ReadiumLink>): FeedDRMLicensor? {
    for (link in links) {
      val props = link.properties

      // Check for a "licensor" property directly on the link
      val licensorObj: Any? = props["licensor"]
      if (licensorObj is Map<*, *>) {
        val vendor = licensorObj["vendor"]?.toString()
        val clientToken = licensorObj["clientToken"]?.toString()
        val deviceManager = licensorObj["deviceManager"]?.toString()

        if (vendor != null && clientToken != null) {
          return FeedDRMLicensor(
            vendor = vendor,
            clientToken = clientToken,
            deviceManager = deviceManager
          )
        }
      }

      // Check for DRM-related rels on the link
      val hasDrmRel = link.rels.any { rel ->
        rel.contains("drm", ignoreCase = true) ||
          rel.contains("lcp", ignoreCase = true) ||
          rel == REL_DRM_DEVICES
      }

      if (hasDrmRel) {
        val vendor = props["vendor"]?.toString()
        val clientToken = props["clientToken"]?.toString()
        val deviceManager = props["deviceManager"]?.toString()

        if (vendor != null && clientToken != null) {
          return FeedDRMLicensor(
            vendor = vendor,
            clientToken = clientToken,
            deviceManager = deviceManager
          )
        }
      }
    }

    return null
  }

  /**
   * Resolve the href of a link against the given base URL, returning the resolved string.
   */
  private fun linkResolvedHref(link: ReadiumLink, baseUrl: Url?): String =
    try {
      link.url(baseUrl).toString()
    } catch (_: Exception) {
      link.href.toString()
    }

  /**
   * Find the first link with the given rel and return its resolved href as a [URI].
   */
  private fun linkHrefAsURI(
    links: List<ReadiumLink>,
    rel: String,
    baseUrl: Url?
  ): URI? =
    links
      .firstOrNull { rel in it.rels }
      ?.let { link ->
        try {
          URI(link.url(baseUrl).toString())
        } catch (_: Exception) {
          URI(link.href.toString())
        }
      }

  /**
   * Find the first feed-level link with the given rel and return its resolved href as a [URI].
   */
  private fun feedLinkURI(
    links: List<ReadiumLink>,
    rel: String,
    baseUrl: Url
  ): URI? =
    links
      .firstOrNull { rel in it.rels }
      ?.let { link ->
        try {
          URI(link.url(baseUrl).toString())
        } catch (_: Exception) {
          URI(link.href.toString())
        }
      }

  /**
   * Convert a Kotlin [Instant] to a Joda [DateTime] in UTC.
   */
  private fun instantToDateTime(instant: Instant): DateTime =
    DateTime(instant.toEpochMilliseconds(), DateTimeZone.UTC)
}
