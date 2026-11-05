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
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedFacetData
import org.nypl.simplified.feeds.api.FeedFacetSingle
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.feeds.api.FeedIndirectAcquisition
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
            .firstOrNull { "self" in it.rels }
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
        .firstOrNull { "next" in it.rels }
        ?.href
        ?.toString()
        ?.let { URI(it) }

    val feedTitle: String =
      feed.metadata.title

    return if (feedGroups.isEmpty()) {
      FeedData(
        feedID = feedURI.toString(),
        feedSearch = null,
        feedTitle = feedTitle,
        feedUpdated = feedUpdated,
        feedURI = feedURI,
        feedTermsOfService = null,
        feedPrivacyPolicy = null,
        feedAbout = null,
        feedLicenses = null,
        feedNext = feedNext,
        facetsByGroup = facetsByGroup,
        facetsOrder = facets,
        entries = entries
      )
    } else {
      FeedDataWithGroups(
        feedID = feedURI.toString(),
        feedSearch = null,
        feedTitle = feedTitle,
        feedUpdated = feedUpdated,
        feedURI = feedURI,
        feedTermsOfService = null,
        feedPrivacyPolicy = null,
        feedAbout = null,
        feedLicenses = null,
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
      metadata.authors.map { it.localizedName?.string ?: "" }.filter { it.isNotEmpty() }

    val narrators: List<String> =
      metadata.narrators.map { it.localizedName?.string ?: "" }.filter { it.isNotEmpty() }

    val publisher: String? =
      metadata.publishers.firstOrNull()?.localizedName?.string

    val language: String? =
      metadata.languages.firstOrNull()

    val summary: String =
      metadata.description ?: ""

    val duration: Double? =
      metadata.duration

    val categories: List<FeedCategory> =
      metadata.subjects.map { convertSubject(it) }

    val series: FeedSeries? =
      metadata.belongsTo
        ?.entries
        ?.firstOrNull()
        ?.let { (name, contributors) ->
          contributors.firstOrNull()?.let {
            FeedSeries(
              name = name,
              position = it.position?.toInt(),
              link = URI(it.links.firstOrNull()?.href?.toString() ?: "")
            )
          }
        }

    val baseUrl: Url? =
      publication.baseUrl

    val acquisitions: List<FeedAcquisition> =
      publication.links
        .filter { it.rels.any { rel -> "/acquire/" in rel } }
        .map { convertAcquisitionLink(it, baseUrl) }

    val cover: URI? =
      publication.links
        .firstOrNull { "cover" in it.rels }
        ?.href
        ?.toString()
        ?.let { URI(it) }

    val thumbnail: URI? =
      publication.links
        .firstOrNull { "thumbnail" in it.rels }
        ?.href
        ?.toString()
        ?.let { URI(it) }

    val availability: FeedAvailability =
      convertAvailability(publication)

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
      previewAcquisitions = emptyList(),
      annotations = null,
      groups = emptySet(),
      issues = null,
      related = null,
      published = published,
      publisher = publisher,
      distribution = "",
      summary = summary,
      narrators = narrators,
      thumbnail = thumbnail,
      timeTrackingUri = null,
      alternate = null,
      analytics = null,
      licensor = null,
      duration = duration,
      language = language,
      audience = null,
      series = series
    )
  }

  private fun convertFacet(accountID: AccountID, facet: Facet): FeedFacetData {
    val uri: URI =
      facet.links
        .firstOrNull { "self" in it.rels }
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
      label = subject.localizedName?.string
    )

  private fun convertAcquisitionLink(link: ReadiumLink, baseUrl: Url?): FeedAcquisition {
    val href: String =
      if (baseUrl != null) {
        link.href.resolve(baseUrl)?.toString() ?: link.href.toString()
      } else {
        link.href.toString()
      }

    val mediaType: MIMEType =
      link.mediaType?.let { parseRaisingException(it.toString()) }
        ?: parseRaisingException("application/octet-stream")

    val indirectAcquisitions: List<FeedIndirectAcquisition> =
      link.properties?.indirectAcquisitions
        ?.map { convertIndirectAcquisition(it) }
        ?: emptyList()

    return FeedAcquisition(
      relation = inferRelation(link.rels),
      uri = LinkBasic(URI(href)),
      type = mediaType,
      indirectAcquisitions = indirectAcquisitions,
      properties = emptyMap()
    )
  }

  private fun convertIndirectAcquisition(acquisition: Acquisition): FeedIndirectAcquisition =
    FeedIndirectAcquisition(
      type = parseRaisingException(acquisition.type),
      indirectAcquisitions = acquisition.children.map { convertIndirectAcquisition(it) },
      properties = emptyMap()
    )

  private fun inferRelation(rels: Set<String>): FeedAcquisitionRelation =
    when {
      rels.any { it.endsWith("/acquire/open-access") } -> FeedAcquisitionRelation.ACQUISITION_OPEN_ACCESS
      rels.any { it.endsWith("/acquire/sample") } -> FeedAcquisitionRelation.ACQUISITION_SAMPLE
      rels.any { it.endsWith("/acquire/buy") } -> FeedAcquisitionRelation.ACQUISITION_BUY
      rels.any { it.endsWith("/acquire/subscribe") } -> FeedAcquisitionRelation.ACQUISITION_SUBSCRIBE
      else -> FeedAcquisitionRelation.ACQUISITION_BORROW
    }

  private fun convertAvailability(publication: Publication): FeedAvailability {
    val availability: Availability? =
      publication.links
        .mapNotNull { it.properties?.availability }
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
        val hasOpenAccess = publication.links.any { it.rels.any { rel -> "/acquire/" in rel } && it.rels.any { it.endsWith("/acquire/open-access") } }
        if (hasOpenAccess) {
          FeedAvailabilityOpenAccess(endDate = null, revoke = null)
        } else {
          FeedAvailabilityLoanable
        }
      }
    }
  }

  /**
   * Convert a Kotlin [Instant] to a Joda [DateTime] in UTC.
   */
  private fun instantToDateTime(instant: Instant): DateTime =
    DateTime(instant.toEpochMilliseconds(), DateTimeZone.UTC)
}
