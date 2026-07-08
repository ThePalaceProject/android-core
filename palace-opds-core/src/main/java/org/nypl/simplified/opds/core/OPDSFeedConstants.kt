package org.nypl.simplified.opds.core

import java.net.URI

object OPDSFeedConstants {
  val ATOM_URI: URI = URI.create("http://www.w3.org/2005/Atom")

  val DUBLIN_CORE_TERMS_URI: URI = URI.create("http://purl.org/dc/terms/")

  val BIBFRAME_URI: URI = URI.create("http://bibframe.org/vocab/")

  val ACQUISITION_URI_PREFIX: URI =
    URI.create("http://opds-spec.org/acquisition")

  val FACET_URI: URI =
    URI.create("http://opds-spec.org/facet")

  val OPDS_URI: URI =
    URI.create("http://opds-spec.org/2010/catalog")

  val ODL_URI: URI =
    URI.create("http://drafts.opds.io/odl-1.0#")

  val DRM_URI: URI =
    URI.create("http://librarysimplified.org/terms/drm")

  val THUMBNAIL_URI: URI =
    URI.create("http://opds-spec.org/image/thumbnail")

  val AUTHENTICATION_DOCUMENT_RELATION_URI: URI =
    URI.create("http://opds-spec.org/auth/document")

  val IMAGE_URI: URI =
    URI.create("http://opds-spec.org/image")

  val SCHEMA_URI: URI =
    URI.create("http://schema.org/")

  val SIMPLIFIED_URI: URI =
    URI.create("http://librarysimplified.org/terms/")

  val REVOKE_URI: URI =
    URI.create("http://librarysimplified.org/terms/rel/revoke")

  val ANNOTATION_URI: URI =
    URI.create("http://www.w3.org/ns/oa#annotationService")

  val LCP_URI: URI =
    URI.create("http://readium.org/lcp-specs/ns")

  val TIME_TRACKING_URI: URI =
    URI.create("http://palaceproject.io/terms/timeTracking")

  val ACQUISITION_URI_PREFIX_TEXT: String =
    ACQUISITION_URI_PREFIX.toString()

  val ATOM_URI_TEXT: String =
    ATOM_URI.toString()

  val ODL_URI_TEXT: String =
    ODL_URI.toString()

  val FACET_URI_TEXT: String =
    FACET_URI.toString()

  val OPDS_URI_TEXT: String =
    OPDS_URI.toString()

  val DRM_URI_TEXT: String =
    DRM_URI.toString()

  val THUMBNAIL_URI_TEXT: String =
    THUMBNAIL_URI.toString()

  val IMAGE_URI_TEXT: String =
    IMAGE_URI.toString()

  val DUBLIN_CORE_TERMS_URI_TEXT: String =
    DUBLIN_CORE_TERMS_URI.toString()

  val SCHEMA_URI_TEXT: String =
    SCHEMA_URI.toString()

  val SIMPLIFIED_URI_TEXT: String =
    SIMPLIFIED_URI.toString()

  val AUTHENTICATION_DOCUMENT_RELATION_URI_TEXT: String =
    AUTHENTICATION_DOCUMENT_RELATION_URI.toString()

  val REVOKE_URI_TEXT: String =
    REVOKE_URI.toString()

  val ANNOTATION_URI_TEXT: String =
    ANNOTATION_URI.toString()

  val LCP_URI_TEXT: String =
    LCP_URI.toString()

  val TIME_TRACKING_URI_TEXT: String =
    TIME_TRACKING_URI.toString()

  const val GROUP_REL_TEXT: String = "collection"

  const val ISSUES_REL_TEXT: String = "issues"

  const val ALTERNATE_REL_TEXT: String = "alternate"

  const val RELATED_REL_TEXT: String = "related"

  const val PREVIEW_TEXT: String = "preview"

  const val SAMPLE_TEXT: String =
    "http://opds-spec.org/acquisition/sample"

  const val CIRCULATION_ANALYTICS_OPEN_BOOK_REL_TEXT: String =
    "http://librarysimplified.org/terms/rel/analytics/open-book"
}
