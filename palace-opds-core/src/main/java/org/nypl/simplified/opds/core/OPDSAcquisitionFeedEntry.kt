package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.nypl.simplified.parser.api.ParseError
import java.io.Serializable
import java.net.URI

/**
 * The type of entries in acquisition feeds.
 */
data class OPDSAcquisitionFeedEntry(
  /**
   * @return The list of acquisitions
   */
  val acquisitions: List<OPDSAcquisition>,

  /**
   * @return The list of authors
   */
  val authors: List<String>,

  /**
   * @return The entry availability
   */
  val availability: OPDSAvailabilityType,

  /**
   * @return The list of categories
   */
  val categories: List<OPDSCategory>,

  /**
   * @return The cover image
   */
  val cover: URI?,

  /**
   * @return The list of preview acquisitions
   */
  val previewAcquisitions: List<OPDSPreviewAcquisition>,

  /**
   * @return the annotations url
   */
  val annotations: URI?,

  /**
   * @return The groups
   */
  val groups: Set<Pair<String, URI>>,

  /**
   * @return The entry ID
   */
  val id: String,

  /**
   * @return The report issues URI
   */
  val issues: URI?,

  /**
   * @return The related feed url
   */
  val related: URI?,

  /**
   * @return The entry publication date, if any
   */
  val published: DateTime?,

  /**
   * @return The publisher, if any
   */
  val publisher: String?,

  /**
   * @return The distribution, if any
   */
  val distribution: String,

  /**
   * @return The summary
   */
  val summary: String,

  /**
   * @return The list of narrators
   */
  val narrators: List<String>,

  /**
   * @return The thumbnail, if any
   */
  val thumbnail: URI?,

  /**
   * @return The time tracking uri, if any
   */
  val timeTrackingUri: URI?,

  /**
   * @return The title
   */
  val title: String,

  /**
   * @return The time of the last update
   */
  val updated: DateTime,

  /**
   * @return alternate url
   */
  val alternate: URI?,

  /**
   * @return analytics url
   */
  val analytics: URI?,

  /**
   * @return The licensor
   */
  val licensor: DRMLicensor?,

  /**
   * @return The list of parse errors encountered, if any
   */
  val errors: List<ParseError>,

  /**
   * @return Duration in seconds
   */
  val duration: Double?,

  /**
   * @return language, if any
   */
  val language: String?,

  /**
   * @return audience, if any
   */
  val audience: String?,

  /**
   * @return series, if any
   */

  val series: OPDSSeries?
) : Serializable {

  companion object {
    @JvmStatic
    fun newBuilder(
      id: String,
      title: String,
      updated: DateTime,
      availability: OPDSAvailabilityType
    ): OPDSAcquisitionFeedEntryBuilderType {
      return Builder(id, title, updated, availability)
    }
  }

  /**
   * The authors as a comma separated string
   */
  val authorsCommaSeparated: String
    get() {
      val sb = StringBuilder()
      val list = this.authors
      val max = list.size

      for (i in 0 until max) {
        val author = list[i]
        sb.append(author)
        if (i + 1 < max) {
          sb.append(", ")
        }
      }
      return sb.toString()
    }

  /**
   * Convert to a mutable builder.
   */
  fun toBuilder(): Builder {
    val b = Builder(
      this.id,
      this.title,
      this.updated,
      this.availability
    )

    b.acquisitionList.addAll(this.acquisitions)
    b.authors.addAll(this.authors)
    b.categories.addAll(this.categories)
    b.groups.addAll(this.groups)
    b.previewAcquisitions.addAll(this.previewAcquisitions)
    b.narrators.addAll(this.narrators)
    b.errors.addAll(this.errors)

    b.alternate = this.alternate
    b.analytics = this.analytics
    b.annotations = this.annotations
    b.audience = this.audience
    b.cover = this.cover
    b.distribution = this.distribution
    b.duration = this.duration
    b.issues = this.issues
    b.language = this.language
    b.licensor = this.licensor
    b.published = this.published
    b.publisher = this.publisher
    b.related = this.related
    b.series = this.series
    b.summary = this.summary
    b.thumbnail = this.thumbnail
    b.timeTrackingUri = this.timeTrackingUri

    return b
  }

  /**
   * Mutable builder for OPDSAcquisitionFeedEntry.
   */
  class Builder(
    private val id: String,
    private val title: String,
    private val updated: DateTime,
    private var availability: OPDSAvailabilityType
  ) : OPDSAcquisitionFeedEntryBuilderType {

    val acquisitionList = mutableListOf<OPDSAcquisition>()
    val authors = mutableListOf<String>()
    val categories = mutableListOf<OPDSCategory>()
    val groups = mutableSetOf<Pair<String, URI>>()
    val previewAcquisitions = mutableListOf<OPDSPreviewAcquisition>()
    val narrators = mutableListOf<String>()
    val errors = mutableListOf<ParseError>()

    var cover: URI? = null
    var annotations: URI? = null
    var issues: URI? = null
    var related: URI? = null
    var published: DateTime? = null
    var publisher: String? = null
    var distribution: String = ""
    var summary: String = ""
    var thumbnail: URI? = null
    var timeTrackingUri: URI? = null
    var alternate: URI? = null
    var analytics: URI? = null
    var licensor: DRMLicensor? = null
    var duration: Double? = null
    var language: String? = null
    var audience: String? = null
    var series: OPDSSeries? = null

    override fun addParseError(error: ParseError): Builder {
      this.errors += error
      return this
    }

    override fun addAcquisition(a: OPDSAcquisition): Builder {
      this.acquisitionList += a
      return this
    }

    override fun addAuthor(name: String): Builder {
      this.authors += name
      return this
    }

    override fun addCategory(c: OPDSCategory): Builder {
      this.categories += c
      return this
    }

    override fun addGroup(uri: URI, name: String): Builder {
      this.groups += (name to uri)
      return this
    }

    override fun addPreviewAcquisition(previewAcquisition: OPDSPreviewAcquisition): Builder {
      this.previewAcquisitions += previewAcquisition
      return this
    }

    override fun addNarrator(name: String): Builder {
      this.narrators += name
      return this
    }

    override fun setAvailability(a: OPDSAvailabilityType): Builder {
      this.availability = a
      return this
    }

    override fun setCoverOption(uri: URI?): Builder {
      this.cover = uri
      return this
    }

    override fun setAnnotationsOption(uri: URI?): Builder {
      this.annotations = uri
      return this
    }

    override fun setAlternateOption(uri: URI?): Builder {
      this.alternate = uri
      return this
    }

    override fun setAnalyticsOption(uri: URI?): Builder {
      this.analytics = uri
      return this
    }

    override fun setIssuesOption(uri: URI?): Builder {
      this.issues = uri
      return this
    }

    override fun setRelatedOption(uri: URI?): Builder {
      this.related = uri
      return this
    }

    override fun setPublishedOption(pub: DateTime?): Builder {
      this.published = pub
      return this
    }

    override fun setPublisherOption(pub: String?): Builder {
      this.publisher = pub
      return this
    }

    override fun setDistribution(dist: String): Builder {
      this.distribution = dist
      return this
    }

    override fun setSummaryOption(text: String?): Builder {
      if (text == null) {
        this.summary = ""
      } else {
        try {
          this.summary = Jsoup.parse(text).text()
        } catch (_: Exception) {
          this.summary = text
        }
      }
      return this
    }

    override fun setThumbnailOption(uri: URI?): Builder {
      this.thumbnail = uri
      return this
    }

    override fun setTimeTrackingUriOption(uri: URI?): Builder {
      this.timeTrackingUri = uri
      return this
    }

    override fun setLicensorOption(licensor: DRMLicensor?): Builder {
      this.licensor = licensor
      return this
    }

    override fun setDurationOption(duration: Double?): Builder {
      this.duration = duration
      return this
    }

    override fun setLanguageOption(language: String?): Builder {
      this.language = language
      return this
    }

    override fun setAudienceOption(audience: String?): Builder {
      this.audience = audience
      return this
    }

    override fun setSeries(series: OPDSSeries?): OPDSAcquisitionFeedEntryBuilderType {
      this.series = series
      return this
    }

    override fun getAcquisitions(): List<OPDSAcquisition> {
      return this.acquisitionList
    }

    override fun build(): OPDSAcquisitionFeedEntry {
      return OPDSAcquisitionFeedEntry(
        acquisitions = this.acquisitionList.toList(),
        alternate = this.alternate,
        analytics = this.analytics,
        annotations = this.annotations,
        audience = this.audience,
        authors = this.authors.toList(),
        availability = this.availability,
        categories = this.categories.toList(),
        cover = this.cover,
        distribution = this.distribution,
        duration = this.duration,
        errors = this.errors.toList(),
        groups = this.groups.toSet(),
        id = this.id,
        issues = this.issues,
        language = this.language,
        licensor = this.licensor,
        narrators = this.narrators.toList(),
        previewAcquisitions = this.previewAcquisitions.toList(),
        published = this.published,
        publisher = this.publisher,
        related = this.related,
        series = this.series,
        summary = this.summary,
        thumbnail = this.thumbnail,
        timeTrackingUri = this.timeTrackingUri,
        title = this.title,
        updated = this.updated,
      )
    }
  }
}
