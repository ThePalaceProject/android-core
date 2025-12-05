package org.nypl.simplified.opds2

import java.net.URI
import java.time.OffsetDateTime

/**
 * Metadata for an OPDS 2.0 feed.
 */

interface OPDS2MetadataType : OPDS2ElementType {

  /**
   * The unique identifier for the publication.
   */

  val identifier: URI?

  /**
   * The title of the publication.
   */

  val title: OPDS2Title

  /**
   * The subtitle of the publication.
   */

  val subtitle: OPDS2Title?

  /**
   * The description of the publication.
   */

  val description: String?

  /**
   * The time the publication was last modified.
   */

  val modified: OffsetDateTime?

  /**
   * The time the publication was published.
   */

  val published: OffsetDateTime?

  /**
   * The languages that apply to the publication.
   */

  val languages: List<String>

  /**
   * The text value used to sort the publication.
   */

  val sortAs: String?

  /**
   * The authors.
   */

  val author: List<OPDS2Contributor>
}
