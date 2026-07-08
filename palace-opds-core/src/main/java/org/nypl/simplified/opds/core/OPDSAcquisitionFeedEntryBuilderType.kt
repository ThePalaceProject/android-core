package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import org.nypl.simplified.parser.api.ParseError
import java.net.URI

/**
 * The type of mutable builders for {@link OPDSAcquisitionFeedEntry} values.
 */

interface OPDSAcquisitionFeedEntryBuilderType {
  /**
   * Add a parse error.
   *
   * @param error The parse error
   */
  fun addParseError(error: ParseError): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Add an acquisition.
   *
   * @param a The acquisition
   */
  fun addAcquisition(a: OPDSAcquisition): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Set the author.
   *
   * @param name The author
   */
  fun addAuthor(name: String): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Add a category.
   *
   * @param c The category
   */
  fun addCategory(c: OPDSCategory): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Add a group.
   *
   * @param uri The group URI
   * @param b   The group name
   */
  fun addGroup(
    uri: URI,
    name: String
  ): OPDSAcquisitionFeedEntryBuilderType

  /**
   * @return An entry based on all of the given values
   */
  fun build(): OPDSAcquisitionFeedEntry

  /**
   * @return A list of the current acquisitions
   */
  fun getAcquisitions(): List<OPDSAcquisition>

  /**
   * Set the availability.
   *
   * @param a The availability
   */
  fun setAvailability(a: OPDSAvailabilityType): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Set the cover.
   *
   * @param uri The cover URI (nullable replacement for OptionType)
   */
  fun setCoverOption(uri: URI?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Add a preview.
   *
   * @param previewAcquisition The preview acquisition
   */
  fun addPreviewAcquisition(previewAcquisition: OPDSPreviewAcquisition): OPDSAcquisitionFeedEntryBuilderType

  /**
   * @param uri The annotations URI (nullable replacement for OptionType)
   */
  fun setAnnotationsOption(uri: URI?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * @param uri The alternate URI (nullable replacement for OptionType)
   */
  fun setAlternateOption(uri: URI?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * @param uri The analytics URI (nullable replacement for OptionType)
   */
  fun setAnalyticsOption(uri: URI?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Set the report issues URI.
   *
   * @param uri The report issues URI (nullable replacement for OptionType)
   */
  fun setIssuesOption(uri: URI?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * @param uri The Related feed URI (nullable replacement for OptionType)
   */
  fun setRelatedOption(uri: URI?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Set the publication date.
   *
   * @param pub The publication date (nullable replacement for OptionType)
   */
  fun setPublishedOption(pub: DateTime?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Set the publisher.
   *
   * @param pub The publisher (nullable replacement for OptionType)
   */
  fun setPublisherOption(pub: String?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Set the distribution.
   *
   * @param dist The distribution
   */
  fun setDistribution(dist: String): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Set the summary.
   *
   * @param text The summary (nullable replacement for OptionType)
   */
  fun setSummaryOption(text: String?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Set the narrator.
   *
   * @param name The narrator's name
   */
  fun addNarrator(name: String): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Set the thumbnail.
   *
   * @param uri The thumbnail (nullable replacement for OptionType)
   */
  fun setThumbnailOption(uri: URI?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * Set the time tracking uri.
   *
   * @param uri The time tracking uri (nullable replacement for OptionType)
   */
  fun setTimeTrackingUriOption(uri: URI?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * @param licensor The Licensor (nullable replacement for OptionType)
   */
  fun setLicensorOption(licensor: DRMLicensor?): OPDSAcquisitionFeedEntryBuilderType

  /**
   * @param duration The duration in seconds (nullable replacement for OptionType)
   */
  fun setDurationOption(duration: Double?): OPDSAcquisitionFeedEntryBuilderType

  fun setLanguageOption(language: String?): OPDSAcquisitionFeedEntryBuilderType

  fun setAudienceOption(audience: String?): OPDSAcquisitionFeedEntryBuilderType

  fun setSeries(series: OPDSSeries?): OPDSAcquisitionFeedEntryBuilderType
}
