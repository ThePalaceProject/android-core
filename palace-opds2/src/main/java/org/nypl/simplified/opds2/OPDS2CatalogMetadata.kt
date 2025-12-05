package org.nypl.simplified.opds2

import java.net.URI
import java.time.OffsetDateTime

data class OPDS2CatalogMetadata(

  /**
   * The unique identifier for the publication.
   */

  override val identifier: URI?,

  /**
   * The title of the publication.
   */

  override val title: OPDS2Title,

  /**
   * The subtitle of the publication.
   */

  override val subtitle: OPDS2Title?,

  /**
   * The description of the publication.
   */

  override val description: String?,

  /**
   * The time the publication was last modified.
   */

  override val modified: OffsetDateTime?,

  /**
   * The time the publication was published.
   */

  override val published: OffsetDateTime?,

  /**
   * The languages that apply to the publication.
   */

  override val languages: List<String>,

  /**
   * The text value used to sort the publication.
   */

  override val sortAs: String?,

  /**
   * The authors.
   */

  override val author: List<OPDS2Contributor>
) : Comparable<OPDS2CatalogMetadata>, OPDS2MetadataType {

  override fun compareTo(other: OPDS2CatalogMetadata): Int =
    (this.sortAs ?: this.title.title).compareTo(other.sortAs ?: other.title.title)
}
