package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import org.nypl.simplified.parser.api.ParseError
import java.io.Serializable
import java.net.URI

/**
 * The type of OPDS acquisition feeds.
 */
data class OPDSAcquisitionFeed(

  /**
   * @return The feed URI
   */
  val feedURI: URI,

  /**
   * @return The list of feed entries
   */
  val feedEntries: List<OPDSAcquisitionFeedEntry>,

  /**
   * @return The feed groups, by name
   */
  val feedGroups: Map<String, OPDSGroup>,

  /**
   * @return The feed groups, in declaration order
   */
  val feedGroupsOrder: List<String>,

  /**
   * @return The feed ID
   */
  val feedID: String,

  /**
   * @return The feed update time
   */
  val feedUpdated: DateTime,

  /**
   * @return The feed title
   */
  val feedTitle: String,

  /**
   * @return The link to the next feed, if any
   */
  val feedNext: URI?,

  /**
   * @return The search document, if any
   */
  val feedSearchURI: OPDSSearchLink?,

  /**
   * @return The feed facets, in order
   */
  val feedFacetsOrder: List<OPDSFacet>,

  /**
   * @return The feed facets, by group
   */
  val feedFacetsByGroup: Map<String, List<OPDSFacet>>,

  /**
   * @return The link to the terms of service, if any
   */
  val feedTermsOfService: URI?,

  /**
   * @return The link to the app about, if any
   */
  val feedAbout: URI?,

  /**
   * @return The link to the privacy policy, if any
   */
  val feedPrivacyPolicy: URI?,

  /**
   * @return The link to the app about, if any
   */
  val feedLicenses: URI?,

  val licensor: DRMLicensor?,

  /**
   * @return The parse errors
   */
  val errors: List<ParseError>,

  /**
   * @return The link to the authentication document, if any
   */
  val authDocument: URI?,

  val annotations: URI?
) : Serializable {

  /**
   * Create a builder initialized from this feed.
   */
  fun toBuilder(): OPDSAcquisitionFeedBuilderType {
    val builder =
      Builder(
        inUri = this.feedURI,
        inTitle = this.feedTitle,
        inId = this.feedID,
        inUpdated = this.feedUpdated
      )

    for (entry in this.feedEntries) {
      builder.addEntry(entry)
    }

    for (facet in this.feedFacetsOrder) {
      builder.addFacet(facet)
    }

    builder.setNextOption(this.feedNext)
    builder.setSearchOption(this.feedSearchURI)
    builder.setTermsOfServiceOption(this.feedTermsOfService)
    builder.setAboutOption(this.feedAbout)
    builder.setPrivacyPolicyOption(this.feedPrivacyPolicy)
    builder.setLicensor(this.licensor)
    builder.setAuthenticationDocumentLink(this.authDocument)
    builder.setAnnotationsOption(this.annotations)

    return builder
  }

  private class Builder(
    inUri: URI,
    inTitle: String,
    inId: String,
    inUpdated: DateTime
  ) : OPDSAcquisitionFeedBuilderType {

    private val entries = mutableListOf<OPDSAcquisitionFeedEntry>()

    private val facetsByGroup =
      mutableMapOf<String, MutableList<OPDSFacet>>()

    private val facetsOrder =
      mutableListOf<OPDSFacet>()

    private val groupUris =
      mutableMapOf<String, URI>()

    private val groups =
      mutableMapOf<String, MutableList<OPDSAcquisitionFeedEntry>>()

    private val groupsOrder =
      mutableListOf<String>()

    private val errors =
      mutableListOf<ParseError>()

    private val id = inId
    private val title = inTitle
    private val updated = inUpdated
    private val uri = inUri

    private var next: URI? = null
    private var search: OPDSSearchLink? = null
    private var termsOfService: URI? = null
    private var privacyPolicy: URI? = null
    private var about: URI? = null
    private var licenses: URI? = null
    private var licensor: DRMLicensor? = null
    private var authDocument: URI? = null
    private var annotations: URI? = null

    override fun addParseError(
      error: ParseError
    ): OPDSAcquisitionFeedBuilderType {
      this.errors += error
      return this
    }

    override fun addEntry(
      e: OPDSAcquisitionFeedEntry
    ): OPDSAcquisitionFeedBuilderType {
      if (e.groups.isEmpty()) {
        this.entries += e
      } else {
        for ((groupName, groupUri) in e.groups) {
          val groupEntries =
            this.groups.getOrPut(groupName) {
              this.groupsOrder += groupName
              mutableListOf()
            }

          groupEntries += e
          this.groupUris[groupName] = groupUri
        }
      }

      this.errors += e.errors

      return this
    }

    override fun addFacet(
      f: OPDSFacet
    ): OPDSAcquisitionFeedBuilderType {
      val facets =
        this.facetsByGroup.getOrPut(f.group) {
          mutableListOf()
        }

      facets += f
      this.facetsOrder += f

      return this
    }

    override fun setAboutOption(
      u: URI?
    ): OPDSAcquisitionFeedBuilderType {
      this.about = u
      return this
    }

    override fun setTermsOfServiceOption(
      u: URI?
    ): OPDSAcquisitionFeedBuilderType {
      this.termsOfService = u
      return this
    }

    override fun setLicensor(
      licensor: DRMLicensor?
    ): OPDSAcquisitionFeedBuilderType {
      this.licensor = licensor
      return this
    }

    override fun setAuthenticationDocumentLink(
      u: URI?
    ): OPDSAcquisitionFeedBuilderType {
      this.authDocument = u
      return this
    }

    override fun setAnnotationsOption(
      u: URI?
    ): OPDSAcquisitionFeedBuilderType {
      this.annotations = u
      return this
    }

    override fun setPrivacyPolicyOption(
      u: URI?
    ): OPDSAcquisitionFeedBuilderType {
      this.privacyPolicy = u
      return this
    }

    override fun setNextOption(
      next: URI?
    ): OPDSAcquisitionFeedBuilderType {
      this.next = next
      return this
    }

    override fun setSearchOption(
      searchLink: OPDSSearchLink?
    ): OPDSAcquisitionFeedBuilderType {
      this.search = searchLink
      return this
    }

    override fun build(): OPDSAcquisitionFeed {
      val resolvedGroups =
        mutableMapOf<String, OPDSGroup>()

      for ((name, entries) in this.groups) {
        resolvedGroups[name] =
          OPDSGroup(
            title = name,
            uri = requireNotNull(this.groupUris[name]),
            entries = entries
          )
      }

      return OPDSAcquisitionFeed(
        feedURI = this.uri,
        feedEntries = this.entries.toList(),
        feedGroups = resolvedGroups,
        feedGroupsOrder = this.groupsOrder.toList(),
        feedID = this.id,
        feedUpdated = this.updated,
        feedTitle = this.title,
        feedNext = this.next,
        feedSearchURI = this.search,
        feedFacetsOrder = this.facetsOrder.toList(),
        feedFacetsByGroup = this.facetsByGroup.mapValues { (_, v) -> v.toList() },
        feedTermsOfService = this.termsOfService,
        feedAbout = this.about,
        feedPrivacyPolicy = this.privacyPolicy,
        feedLicenses = this.licenses,
        licensor = this.licensor,
        errors = this.errors.toList(),
        authDocument = this.authDocument,
        annotations = this.annotations
      )
    }
  }

  companion object {
    private const val serialVersionUID = 1L

    /**
     * Construct an acquisition feed builder.
     *
     * @param inUri The feed URI
     * @param inId The feed ID
     * @param inUpdated The feed updated date
     * @param inTitle The feed title
     * @return A new builder
     */
    @JvmStatic
    fun newBuilder(
      inUri: URI,
      inId: String,
      inUpdated: DateTime,
      inTitle: String
    ): OPDSAcquisitionFeedBuilderType {
      return Builder(
        inUri = inUri,
        inTitle = inTitle,
        inId = inId,
        inUpdated = inUpdated
      )
    }

    /**
     * Construct a builder initialized from an existing feed.
     */
    @JvmStatic
    fun newBuilderFrom(
      feed: OPDSAcquisitionFeed
    ): OPDSAcquisitionFeedBuilderType {
      return feed.toBuilder()
    }
  }
}
