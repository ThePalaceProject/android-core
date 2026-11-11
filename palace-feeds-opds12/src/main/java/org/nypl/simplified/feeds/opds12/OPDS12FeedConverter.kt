package org.nypl.simplified.feeds.opds12

import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.feeds.api.FeedAcquisition
import org.nypl.simplified.feeds.api.FeedAcquisitionRelation
import org.nypl.simplified.feeds.api.FeedAvailability
import org.nypl.simplified.feeds.api.FeedAvailabilityHeld
import org.nypl.simplified.feeds.api.FeedAvailabilityHeldReady
import org.nypl.simplified.feeds.api.FeedAvailabilityHoldable
import org.nypl.simplified.feeds.api.FeedAvailabilityLoanable
import org.nypl.simplified.feeds.api.FeedAvailabilityLoaned
import org.nypl.simplified.feeds.api.FeedAvailabilityOpenAccess
import org.nypl.simplified.feeds.api.FeedAvailabilityRevoked
import org.nypl.simplified.feeds.api.FeedCategory
import org.nypl.simplified.feeds.api.FeedDRMLicensor
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.feeds.api.FeedDataWithGroups
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedEntryGroupRef
import org.nypl.simplified.feeds.api.FeedFacetData
import org.nypl.simplified.feeds.api.FeedFacetSingle
import org.nypl.simplified.feeds.api.FeedGroup
import org.nypl.simplified.feeds.api.FeedIndirectAcquisition
import org.nypl.simplified.feeds.api.FeedPreviewAcquisition
import org.nypl.simplified.feeds.api.FeedSearch
import org.nypl.simplified.feeds.api.FeedSearchOpen1_1
import org.nypl.simplified.feeds.api.FeedSeries
import org.nypl.simplified.opds.core.DRMLicensor
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeed
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAvailabilityHeld
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSAvailabilityRevoked
import org.nypl.simplified.opds.core.OPDSCategory
import org.nypl.simplified.opds.core.OPDSFacet
import org.nypl.simplified.opds.core.OPDSGroup
import org.nypl.simplified.opds.core.OPDSIndirectAcquisition
import org.nypl.simplified.opds.core.OPDSOpenSearch1_1
import org.nypl.simplified.opds.core.OPDSPreviewAcquisition
import org.nypl.simplified.opds.core.OPDSSeries

/**
 * Functions to convert OPDS 1.2 types from [palace-opds-core] into the
 * generic [Feed] types from [palace-feeds-api].
 */
object OPDS12FeedConverter {
  /**
   * Convert an [OPDSAcquisitionFeed] into a [Feed].
   *
   * @param accountID The account that owns the feed
   * @param feed The parsed OPDS 1.2 feed
   * @param filter A predicate to filter entries
   * @param search The parsed OpenSearch 1.1 document, if any
   * @return The converted feed
   */
  fun convert(
    accountID: AccountID,
    feed: OPDSAcquisitionFeed,
    search: OPDSOpenSearch1_1?,
  ): Feed {
    val feedSearch: FeedSearch? =
      if (search != null) {
        FeedSearchOpen1_1(queryFn = search::getQueryURIForTerms)
      } else {
        null
      }

    val facetsByGroup: Map<String, List<FeedFacetSingle>> =
      feed.feedFacetsByGroup.mapValues { (_, facets) ->
        facets.map { convertFacet(accountID, it) }
      }

    val facetsOrder: List<FeedFacetSingle> =
      feed.feedFacetsOrder.map { convertFacet(accountID, it) }

    /*
     * Produce an ordered list of groups from the map + order list.
     */
    val orderedGroups: List<OPDSGroup> =
      feed.feedGroupsOrder.map { name -> feed.feedGroups.getValue(name) }

    return if (orderedGroups.isEmpty()) {
      val entries: List<FeedEntry> =
        feed.feedEntries
          .map { convertEntry(accountID, it) }

      FeedData(
        feedID = feed.feedID,
        feedSearch = feedSearch,
        feedTitle = feed.feedTitle,
        feedUpdated = feed.feedUpdated,
        feedURI = feed.feedURI,
        feedTermsOfService = feed.feedTermsOfService,
        feedPrivacyPolicy = feed.feedPrivacyPolicy,
        feedAbout = feed.feedAbout,
        feedLicenses = feed.feedLicenses,
        feedNext = feed.feedNext,
        facetsByGroup = facetsByGroup,
        facetsOrder = facetsOrder,
        entries = entries,
      )
    } else {
      val feedGroups: List<FeedGroup> =
        orderedGroups.map { group ->
          FeedGroup(
            account = accountID,
            groupTitle = group.title,
            groupURI = group.uri,
            groupEntries =
              group.entries
                .map { convertEntry(accountID, it) },
          )
        }

      FeedDataWithGroups(
        feedID = feed.feedID,
        feedSearch = feedSearch,
        feedTitle = feed.feedTitle,
        feedUpdated = feed.feedUpdated,
        feedURI = feed.feedURI,
        feedTermsOfService = feed.feedTermsOfService,
        feedPrivacyPolicy = feed.feedPrivacyPolicy,
        feedAbout = feed.feedAbout,
        feedLicenses = feed.feedLicenses,
        feedNext = feed.feedNext,
        facetsByGroup = facetsByGroup,
        facetsOrder = facetsOrder,
        groups = feedGroups,
      )
    }
  }

  /**
   * Convert an [OPDSAcquisitionFeedEntry] into a [FeedEntryBook].
   */
  fun convertEntry(
    accountID: AccountID,
    entry: OPDSAcquisitionFeedEntry,
  ): FeedEntryBook {
    val series: FeedSeries? =
      entry.series?.let { s ->
        FeedSeries(
          name = s.name,
          position = s.position,
          link = s.link,
        )
      }

    return FeedEntryBook(
      accountID = accountID,
      id = entry.id,
      title = entry.title,
      updated = entry.updated,
      authors = entry.authors,
      availability = convertAvailability(entry.availability),
      acquisitions = entry.acquisitions.map { convertAcquisition(it) },
      categories = entry.categories.map { convertCategory(it) },
      cover = entry.cover,
      previewAcquisitions = entry.previewAcquisitions.map { convertPreviewAcquisition(it) },
      annotations = entry.annotations,
      groups = entry.groups.map { (name, uri) -> FeedEntryGroupRef(name, uri) }.toSet(),
      issues = entry.issues,
      related = entry.related,
      published = entry.published,
      publisher = entry.publisher,
      distribution = entry.distribution,
      summary = entry.summary,
      narrators = entry.narrators,
      thumbnail = entry.thumbnail,
      timeTrackingUri = entry.timeTrackingUri,
      alternate = entry.alternate,
      analytics = entry.analytics,
      licensor = entry.licensor?.let { convertLicensor(it) },
      duration = entry.duration,
      language = entry.language,
      audience = entry.audience,
      series = series,
    )
  }

  private fun convertFacet(
    accountID: AccountID,
    facet: OPDSFacet,
  ): FeedFacetData =
    FeedFacetData(
      accountID = accountID,
      title = facet.title,
      isActive = facet.isActive,
      uri = facet.uri,
      group = facet.group,
      groupType = facet.groupType,
    )

  private fun convertCategory(category: OPDSCategory): FeedCategory =
    FeedCategory(
      term = category.term,
      scheme = category.scheme,
      label = category.label,
    )

  private fun convertAvailability(availability: org.nypl.simplified.opds.core.OPDSAvailabilityType): FeedAvailability =
    when (availability) {
      is OPDSAvailabilityHeld -> {
        FeedAvailabilityHeld(
          startDate = availability.startDate,
          position = availability.position,
          endDate = availability.endDate,
          revoke = availability.revoke,
        )
      }

      is OPDSAvailabilityHeldReady -> {
        FeedAvailabilityHeldReady(
          endDate = availability.endDate,
          revoke = availability.revoke,
        )
      }

      is OPDSAvailabilityHoldable -> {
        FeedAvailabilityHoldable
      }

      is OPDSAvailabilityLoanable -> {
        FeedAvailabilityLoanable
      }

      is OPDSAvailabilityLoaned -> {
        FeedAvailabilityLoaned(
          startDate = availability.startDate,
          endDate = availability.endDate,
          revoke = availability.revoke,
        )
      }

      is OPDSAvailabilityOpenAccess -> {
        FeedAvailabilityOpenAccess(
          endDate = availability.endDate,
          revoke = availability.revoke,
        )
      }

      is OPDSAvailabilityRevoked -> {
        FeedAvailabilityRevoked(
          endDate = availability.endDate,
          revoke = availability.revoke,
        )
      }
    }

  private fun convertAcquisition(acquisition: OPDSAcquisition): FeedAcquisition =
    FeedAcquisition(
      relation = convertRelation(acquisition.relation),
      uri = acquisition.uri,
      type = acquisition.type,
      indirectAcquisitions = acquisition.indirectAcquisitions.map { convertIndirectAcquisition(it) },
      properties = acquisition.properties,
    )

  private fun convertIndirectAcquisition(indirect: OPDSIndirectAcquisition): FeedIndirectAcquisition =
    FeedIndirectAcquisition(
      type = indirect.type,
      indirectAcquisitions = indirect.indirectAcquisitions.map { convertIndirectAcquisition(it) },
      properties = indirect.properties,
    )

  private fun convertRelation(relation: OPDSAcquisition.Relation): FeedAcquisitionRelation =
    when (relation) {
      OPDSAcquisition.Relation.ACQUISITION_BORROW -> FeedAcquisitionRelation.ACQUISITION_BORROW
      OPDSAcquisition.Relation.ACQUISITION_BUY -> FeedAcquisitionRelation.ACQUISITION_BUY
      OPDSAcquisition.Relation.ACQUISITION_GENERIC -> FeedAcquisitionRelation.ACQUISITION_GENERIC
      OPDSAcquisition.Relation.ACQUISITION_OPEN_ACCESS -> FeedAcquisitionRelation.ACQUISITION_OPEN_ACCESS
      OPDSAcquisition.Relation.ACQUISITION_SAMPLE -> FeedAcquisitionRelation.ACQUISITION_SAMPLE
      OPDSAcquisition.Relation.ACQUISITION_SUBSCRIBE -> FeedAcquisitionRelation.ACQUISITION_SUBSCRIBE
    }

  private fun convertLicensor(licensor: DRMLicensor): FeedDRMLicensor =
    FeedDRMLicensor(
      vendor = licensor.vendor,
      clientToken = licensor.clientToken,
      deviceManager = licensor.deviceManager,
    )

  private fun convertPreviewAcquisition(preview: OPDSPreviewAcquisition): FeedPreviewAcquisition =
    FeedPreviewAcquisition(
      uri = preview.uri,
      type = preview.type,
    )
}
