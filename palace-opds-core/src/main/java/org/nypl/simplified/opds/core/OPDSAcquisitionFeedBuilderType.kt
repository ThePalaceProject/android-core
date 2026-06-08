package org.nypl.simplified.opds.core

import org.nypl.simplified.parser.api.ParseError
import java.net.URI

/**
 * The type of mutable builders for [OPDSAcquisitionFeed] values.
 */
interface OPDSAcquisitionFeedBuilderType {
  /**
   * Add a parse error.
   *
   * @param error The parse error
   */
  fun addParseError(error: ParseError): OPDSAcquisitionFeedBuilderType

  /**
   * Add an entry to the feed being constructed.
   *
   * @param e The entry
   */
  fun addEntry(e: OPDSAcquisitionFeedEntry): OPDSAcquisitionFeedBuilderType

  /**
   * Set the URI of the privacy policy document for the feed, if any
   *
   * @param u The privacy policy URI, if any
   */
  fun setPrivacyPolicyOption(u: URI?): OPDSAcquisitionFeedBuilderType

  /**
   * @return A feed consisting of all the values given so far
   */
  fun build(): OPDSAcquisitionFeed

  /**
   * Set the URI of the next feed in a paginated feed
   *
   * @param next The next URI, if any
   */
  fun setNextOption(next: URI?): OPDSAcquisitionFeedBuilderType

  /**
   * Set the URI of the search facilities for the given feed
   *
   * @param s The search URI, if any
   */
  fun setSearchOption(searchLink: OPDSSearchLink?): OPDSAcquisitionFeedBuilderType

  /**
   * Add the given facet.
   *
   * @param f The facet
   */
  fun addFacet(f: OPDSFacet): OPDSAcquisitionFeedBuilderType

  /**
   * Set the URI of the app about document for the feed, if any
   *
   * @param u The App About URI, if any
   */
  fun setAboutOption(u: URI?): OPDSAcquisitionFeedBuilderType

  /**
   * Set the URI of the terms of service document for the feed, if any
   *
   * @param u The terms of service URI, if any
   */
  fun setTermsOfServiceOption(u: URI?): OPDSAcquisitionFeedBuilderType

  /**
   * @param licensor drm licensor info
   */
  fun setLicensor(licensor: DRMLicensor?): OPDSAcquisitionFeedBuilderType

  /**
   * Set the URI of the authentication document for the feed, if any
   *
   * @param u The authentication document URI, if any
   */
  fun setAuthenticationDocumentLink(u: URI?): OPDSAcquisitionFeedBuilderType

  /**
   * Set the URI of the annotations service for the feed, if any
   *
   * @param u The annotations service URI, if any
   */
  fun setAnnotationsOption(u: URI?): OPDSAcquisitionFeedBuilderType
}
