package org.nypl.simplified.feeds.opds12

import org.nypl.simplified.feeds.api.FeedAcquisition
import org.nypl.simplified.feeds.api.FeedAcquisitionRelation
import org.nypl.simplified.feeds.api.FeedAvailabilityHeld
import org.nypl.simplified.feeds.api.FeedAvailabilityHeldReady
import org.nypl.simplified.feeds.api.FeedAvailabilityHoldable
import org.nypl.simplified.feeds.api.FeedAvailabilityLoanable
import org.nypl.simplified.feeds.api.FeedAvailabilityLoaned
import org.nypl.simplified.feeds.api.FeedAvailabilityOpenAccess
import org.nypl.simplified.feeds.api.FeedAvailabilityRevoked
import org.nypl.simplified.feeds.api.FeedCategory
import org.nypl.simplified.feeds.api.FeedDRMLicensor
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedIndirectAcquisition
import org.nypl.simplified.feeds.api.FeedPreviewAcquisition
import org.nypl.simplified.feeds.api.FeedSeries
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
fun FeedEntryBook.toOPDSAcquisitionFeedEntry(): OPDSAcquisitionFeedEntry {
  val builder =
    OPDSAcquisitionFeedEntry.newBuilder(
      id = this.id,
      title = this.title,
      updated = this.updated,
      availability = this.availability.toOPDS()
    )

  this.authors.forEach { builder.addAuthor(it) }
  this.narrators.forEach { builder.addNarrator(it) }
  this.categories.forEach { builder.addCategory(it.toOPDS()) }
  this.acquisitions.forEach { builder.addAcquisition(it.toOPDS()) }
  this.previewAcquisitions.forEach { builder.addPreviewAcquisition(it.toOPDS()) }
  this.groups.forEach { (name, uri) -> builder.addGroup(uri, name) }

  if (this.published != null) builder.setPublishedOption(this.published)
  if (this.publisher != null) builder.setPublisherOption(this.publisher)
  builder.setDistribution(this.distribution)
  if (this.summary != null) builder.setSummaryOption(this.summary)
  builder.setCoverOption(this.cover)
  builder.setThumbnailOption(this.thumbnail)
  builder.setAnnotationsOption(this.annotations)
  builder.setIssuesOption(this.issues)
  builder.setRelatedOption(this.related)
  builder.setAlternateOption(this.alternate)
  builder.setAnalyticsOption(this.analytics)
  builder.setTimeTrackingUriOption(this.timeTrackingUri)
  builder.setDurationOption(this.duration)
  builder.setLanguageOption(this.language)
  builder.setAudienceOption(this.audience)

  val licensor = this.licensor
  if (licensor != null) {
    builder.setLicensorOption(
      DRMLicensor(
        vendor = licensor.vendor,
        clientToken = licensor.clientToken,
        deviceManager = licensor.deviceManager
      )
    )
  }

  val series = this.series
  if (series != null) {
    builder.setSeries(
      OPDSSeries(
        name = series.name,
        position = series.position,
        link = series.link
      )
    )
  }

  return builder.build()
}

private fun org.nypl.simplified.feeds.api.FeedAvailability.toOPDS(): org.nypl.simplified.opds.core.OPDSAvailabilityType =
  when (this) {
    is FeedAvailabilityHeld ->
      OPDSAvailabilityHeld.get(this.startDate, this.position, this.endDate, this.revoke)
    is FeedAvailabilityHeldReady ->
      OPDSAvailabilityHeldReady.get(this.endDate, this.revoke)
    is FeedAvailabilityHoldable ->
      OPDSAvailabilityHoldable.get()
    is FeedAvailabilityLoanable ->
      OPDSAvailabilityLoanable.get()
    is FeedAvailabilityLoaned ->
      OPDSAvailabilityLoaned.get(this.startDate, this.endDate, this.revoke)
    is FeedAvailabilityOpenAccess ->
      OPDSAvailabilityOpenAccess.get(this.revoke)
    is FeedAvailabilityRevoked ->
      OPDSAvailabilityRevoked.get(this.revoke)
  }

private fun FeedCategory.toOPDS(): OPDSCategory =
  OPDSCategory(
    term = this.term,
    scheme = this.scheme,
    label = this.label
  )

private fun FeedAcquisition.toOPDS(): OPDSAcquisition =
  OPDSAcquisition(
    relation = this.relation.toOPDS(),
    uri = this.uri,
    type = this.type,
    indirectAcquisitions = this.indirectAcquisitions.map { it.toOPDS() },
    properties = this.properties
  )

private fun FeedAcquisitionRelation.toOPDS(): OPDSAcquisition.Relation =
  when (this) {
    FeedAcquisitionRelation.ACQUISITION_BORROW -> OPDSAcquisition.Relation.ACQUISITION_BORROW
    FeedAcquisitionRelation.ACQUISITION_BUY -> OPDSAcquisition.Relation.ACQUISITION_BUY
    FeedAcquisitionRelation.ACQUISITION_GENERIC -> OPDSAcquisition.Relation.ACQUISITION_GENERIC
    FeedAcquisitionRelation.ACQUISITION_OPEN_ACCESS -> OPDSAcquisition.Relation.ACQUISITION_OPEN_ACCESS
    FeedAcquisitionRelation.ACQUISITION_SAMPLE -> OPDSAcquisition.Relation.ACQUISITION_SAMPLE
    FeedAcquisitionRelation.ACQUISITION_SUBSCRIBE -> OPDSAcquisition.Relation.ACQUISITION_SUBSCRIBE
  }

private fun FeedIndirectAcquisition.toOPDS(): OPDSIndirectAcquisition =
  OPDSIndirectAcquisition(
    type = this.type,
    indirectAcquisitions = this.indirectAcquisitions.map { it.toOPDS() },
    properties = this.properties
  )

private fun FeedPreviewAcquisition.toOPDS(): OPDSPreviewAcquisition =
  OPDSPreviewAcquisition(
    uri = this.uri,
    type = this.type
  )
