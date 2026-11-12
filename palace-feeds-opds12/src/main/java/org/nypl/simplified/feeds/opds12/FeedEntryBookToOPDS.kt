package org.nypl.simplified.feeds.opds12

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
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedIndirectAcquisition
import org.nypl.simplified.feeds.api.FeedPreviewAcquisition
import org.nypl.simplified.opds.core.DRMLicensor
import org.nypl.simplified.opds.core.OPDSAcquisition
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAvailabilityHeld
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess
import org.nypl.simplified.opds.core.OPDSAvailabilityRevoked
import org.nypl.simplified.opds.core.OPDSAvailabilityType
import org.nypl.simplified.opds.core.OPDSCategory
import org.nypl.simplified.opds.core.OPDSIndirectAcquisition
import org.nypl.simplified.opds.core.OPDSPreviewAcquisition
import org.nypl.simplified.opds.core.OPDSSeries

/**
 * Convert a [FeedEntryBook] back into an [OPDSAcquisitionFeedEntry].
 *
 * This is a transitional bridge for code that still consumes OPDS 1.2 types
 * directly. It will be removed once all call sites have been migrated to
 * consume [FeedEntryBook] directly.
 */
object FeedEntryBookToOPDS {
  fun toOPDSAcquisitionFeedEntry(entry: FeedEntryBook): OPDSAcquisitionFeedEntry {
    val builder =
      OPDSAcquisitionFeedEntry.newBuilder(
        id = entry.id,
        title = entry.title,
        updated = entry.updated,
        availability = toOPDS(entry.availability),
      )

    entry.authors.forEach { builder.addAuthor(it) }
    entry.narrators.forEach { builder.addNarrator(it) }
    entry.categories.forEach { builder.addCategory(toOPDS(it)) }
    entry.acquisitions.forEach { builder.addAcquisition(toOPDS(it)) }
    entry.previewAcquisitions.forEach { builder.addPreviewAcquisition(toOPDS(it)) }
    entry.groups.forEach { (name, uri) -> builder.addGroup(uri, name) }

    val published = entry.published
    if (published != null) builder.setPublishedOption(published)

    val publisher = entry.publisher
    if (publisher != null) builder.setPublisherOption(publisher)

    builder.setDistribution(entry.distribution)

    val summary = entry.summary
    if (summary != null) builder.setSummaryOption(summary)

    builder.setCoverOption(entry.cover)
    builder.setThumbnailOption(entry.thumbnail)
    builder.setAnnotationsOption(entry.annotations)
    builder.setIssuesOption(entry.issues)
    builder.setRelatedOption(entry.related)
    builder.setAlternateOption(entry.alternate)
    builder.setAnalyticsOption(entry.analytics)
    builder.setTimeTrackingUriOption(entry.timeTrackingUri)
    builder.setDurationOption(entry.duration)
    builder.setLanguageOption(entry.language)
    builder.setAudienceOption(entry.audience)

    val licensor = entry.licensor
    if (licensor != null) {
      builder.setLicensorOption(
        DRMLicensor(
          vendor = licensor.vendor,
          clientToken = licensor.clientToken,
          deviceManager = licensor.deviceManager,
        ),
      )
    }

    val series = entry.series
    if (series != null) {
      val seriesLink = series.link
      if (seriesLink != null) {
        builder.setSeries(
          OPDSSeries(
            name = series.name,
            position = series.position,
            link = seriesLink,
          ),
        )
      }
    }

    return builder.build()
  }

  private fun toOPDS(availability: FeedAvailability): OPDSAvailabilityType {
    return when (availability) {
      is FeedAvailabilityHeld -> {
        OPDSAvailabilityHeld.get(
          availability.startDate,
          availability.position,
          availability.endDate,
          availability.revoke,
        )
      }

      is FeedAvailabilityHeldReady -> {
        OPDSAvailabilityHeldReady.get(availability.endDate, availability.revoke)
      }

      is FeedAvailabilityHoldable -> {
        OPDSAvailabilityHoldable.get()
      }

      is FeedAvailabilityLoanable -> {
        OPDSAvailabilityLoanable.get()
      }

      is FeedAvailabilityLoaned -> {
        OPDSAvailabilityLoaned.get(
          availability.startDate,
          availability.endDate,
          availability.revoke,
        )
      }

      is FeedAvailabilityOpenAccess -> {
        OPDSAvailabilityOpenAccess.get(availability.revoke)
      }

      is FeedAvailabilityRevoked -> {
        OPDSAvailabilityRevoked.get(availability.revoke)
      }
    }
  }

  private fun toOPDS(category: FeedCategory): OPDSCategory {
    return OPDSCategory(
      term = category.term,
      scheme = category.scheme,
      label = category.label,
    )
  }

  private fun toOPDS(acquisition: FeedAcquisition): OPDSAcquisition {
    return OPDSAcquisition(
      relation = toOPDS(acquisition.relation),
      uri = acquisition.uri,
      type = acquisition.type,
      indirectAcquisitions = acquisition.indirectAcquisitions.map { toOPDS(it) },
      properties = acquisition.properties,
    )
  }

  private fun toOPDS(relation: FeedAcquisitionRelation): OPDSAcquisition.Relation {
    return when (relation) {
      FeedAcquisitionRelation.ACQUISITION_BORROW -> OPDSAcquisition.Relation.ACQUISITION_BORROW
      FeedAcquisitionRelation.ACQUISITION_BUY -> OPDSAcquisition.Relation.ACQUISITION_BUY
      FeedAcquisitionRelation.ACQUISITION_GENERIC -> OPDSAcquisition.Relation.ACQUISITION_GENERIC
      FeedAcquisitionRelation.ACQUISITION_OPEN_ACCESS -> OPDSAcquisition.Relation.ACQUISITION_OPEN_ACCESS
      FeedAcquisitionRelation.ACQUISITION_SAMPLE -> OPDSAcquisition.Relation.ACQUISITION_SAMPLE
      FeedAcquisitionRelation.ACQUISITION_SUBSCRIBE -> OPDSAcquisition.Relation.ACQUISITION_SUBSCRIBE
    }
  }

  private fun toOPDS(indirect: FeedIndirectAcquisition): OPDSIndirectAcquisition {
    return OPDSIndirectAcquisition(
      type = indirect.type,
      indirectAcquisitions = indirect.indirectAcquisitions.map { toOPDS(it) },
      properties = indirect.properties,
    )
  }

  private fun toOPDS(preview: FeedPreviewAcquisition): OPDSPreviewAcquisition {
    return OPDSPreviewAcquisition(
      uri = preview.uri,
      type = preview.type,
    )
  }
}
