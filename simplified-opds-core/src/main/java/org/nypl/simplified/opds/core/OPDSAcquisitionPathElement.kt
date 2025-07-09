package org.nypl.simplified.opds.core

import one.irradia.mime.api.MIMEType
import org.nypl.simplified.links.Link

/**
 * An element of a linearized OPDS acquisition path.
 *
 * @see "https://github.com/io7m/opds-acquisition-spec"
 */

data class OPDSAcquisitionPathElement(
  val mimeType: MIMEType,
  val target: Link?,
  val properties: Map<String, String>
)
