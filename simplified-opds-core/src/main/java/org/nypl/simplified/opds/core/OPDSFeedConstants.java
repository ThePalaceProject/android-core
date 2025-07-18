package org.nypl.simplified.opds.core;

import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;

import java.net.URI;

public final class OPDSFeedConstants {

  public static final String ACQUISITION_URI_PREFIX_TEXT;
  public static final URI ATOM_URI;
  public static final URI ODL_URI;
  public static final String ODL_URI_TEXT;
  public static final URI DUBLIN_CORE_TERMS_URI;
  public static final URI BIBFRAME_URI;
  public static final String FACET_URI_TEXT;
  public static final String GROUP_REL_TEXT;
  public static final String IMAGE_URI_TEXT;
  public static final String SAMPLE_TEXT;
  public static final String PREVIEW_TEXT;
  public static final String OPDS_URI_TEXT;
  public static final String DRM_URI_TEXT;
  public static final String THUMBNAIL_URI_TEXT;
  public static final String ISSUES_REL_TEXT;
  public static final String ALTERNATE_REL_TEXT;
  public static final String CIRCULATION_ANALYTICS_OPEN_BOOK_REL_TEXT;
  public static final String RELATED_REL_TEXT;
  public static final URI SCHEMA_URI;
  public static final URI SIMPLIFIED_URI;
  public static final URI OPDS_URI;
  public static final URI DRM_URI;
  public static final URI REVOKE_URI;
  public static final String REVOKE_URI_TEXT;
  public static final URI ANNOTATION_URI;
  public static final String ANNOTATION_URI_TEXT;
  public static final URI ACQUISITION_URI_PREFIX;
  public static final String ATOM_URI_TEXT;
  public static final URI FACET_URI;
  public static final URI IMAGE_URI;
  public static final URI THUMBNAIL_URI;
  public static final String DUBLIN_CORE_TERMS_URI_TEXT;
  public static final String SCHEMA_URI_TEXT;
  public static final String SIMPLIFIED_URI_TEXT;
  public static final URI AUTHENTICATION_DOCUMENT_RELATION_URI;
  public static final String AUTHENTICATION_DOCUMENT_RELATION_URI_TEXT;
  public static final URI LCP_URI;
  public static final String LCP_URI_TEXT;
  public static final URI TIME_TRACKING_URI;
  public static final String TIME_TRACKING_URI_TEXT;

  static {
    ATOM_URI = NullCheck.notNull(URI.create("http://www.w3.org/2005/Atom"));
    ATOM_URI_TEXT = NullCheck.notNull(OPDSFeedConstants.ATOM_URI.toString());

    BIBFRAME_URI =
      NullCheck.notNull(URI.create("http://bibframe.org/vocab/"));
    DUBLIN_CORE_TERMS_URI =
      NullCheck.notNull(URI.create("http://purl.org/dc/terms/"));
    DUBLIN_CORE_TERMS_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.DUBLIN_CORE_TERMS_URI.toString());

    ACQUISITION_URI_PREFIX =
      NullCheck.notNull(URI.create("http://opds-spec.org/acquisition"));
    ACQUISITION_URI_PREFIX_TEXT =
      NullCheck.notNull(OPDSFeedConstants.ACQUISITION_URI_PREFIX.toString());

    FACET_URI =
      NullCheck.notNull(URI.create("http://opds-spec.org/facet"));
    FACET_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.FACET_URI.toString());

    GROUP_REL_TEXT = "collection";

    OPDS_URI =
      NullCheck.notNull(URI.create("http://opds-spec.org/2010/catalog"));
    OPDS_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.OPDS_URI.toString());

    ODL_URI =
      NullCheck.notNull(URI.create("http://drafts.opds.io/odl-1.0#"));
    ODL_URI_TEXT =
      NullCheck.notNull(ODL_URI.toString());

    DRM_URI =
      NullCheck.notNull(URI.create("http://librarysimplified.org/terms/drm"));
    DRM_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.DRM_URI.toString());

    THUMBNAIL_URI =
      NullCheck.notNull(URI.create("http://opds-spec.org/image/thumbnail"));
    THUMBNAIL_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.THUMBNAIL_URI.toString());

    AUTHENTICATION_DOCUMENT_RELATION_URI =
      NullCheck.notNull(URI.create("http://opds-spec.org/auth/document"));
    AUTHENTICATION_DOCUMENT_RELATION_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.AUTHENTICATION_DOCUMENT_RELATION_URI.toString());

    ISSUES_REL_TEXT = "issues";

    CIRCULATION_ANALYTICS_OPEN_BOOK_REL_TEXT =
      "http://librarysimplified.org/terms/rel/analytics/open-book";

    ALTERNATE_REL_TEXT = "alternate";
    RELATED_REL_TEXT = "related";

    IMAGE_URI =
      NullCheck.notNull(URI.create("http://opds-spec.org/image"));
    IMAGE_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.IMAGE_URI.toString());

    PREVIEW_TEXT = "preview";
    SAMPLE_TEXT = "http://opds-spec.org/acquisition/sample";

    SCHEMA_URI =
      NullCheck.notNull(URI.create("http://schema.org/"));
    SCHEMA_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.SCHEMA_URI.toString());

    SIMPLIFIED_URI =
      NullCheck.notNull(URI.create("http://librarysimplified.org/terms/"));
    SIMPLIFIED_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.SIMPLIFIED_URI.toString());

    REVOKE_URI =
      NullCheck.notNull(URI.create("http://librarysimplified.org/terms/rel/revoke"));
    REVOKE_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.REVOKE_URI.toString());

    ANNOTATION_URI = NullCheck.notNull(
      URI.create("http://www.w3.org/ns/oa#annotationService"));
    ANNOTATION_URI_TEXT =
      NullCheck.notNull(OPDSFeedConstants.ANNOTATION_URI.toString());

    LCP_URI =
      NullCheck.notNull(URI.create("http://readium.org/lcp-specs/ns"));
    LCP_URI_TEXT =
      NullCheck.notNull(LCP_URI.toString());

    TIME_TRACKING_URI =
      NullCheck.notNull(URI.create("http://palaceproject.io/terms/timeTracking"));
    TIME_TRACKING_URI_TEXT =
      NullCheck.notNull(TIME_TRACKING_URI.toString());
  }

  private OPDSFeedConstants() {
    throw new UnreachableCodeException();
  }
}
