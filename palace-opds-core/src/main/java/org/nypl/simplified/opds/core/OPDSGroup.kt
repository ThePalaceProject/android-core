package org.nypl.simplified.opds.core

import java.net.URI

/**
 * An OPDS group.
 *
 * @property title The group title
 * @property uri The group URI
 * @property entries The entries within the group
 */
data class OPDSGroup(
  /**
   * The group title.
   */
  val title: String,

  /**
   * The group URI.
   */
  val uri: URI,

  /**
   * The entries within the group.
   */
  val entries: List<OPDSAcquisitionFeedEntry>
)
