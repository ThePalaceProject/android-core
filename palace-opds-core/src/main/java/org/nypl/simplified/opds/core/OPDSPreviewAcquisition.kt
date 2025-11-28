package org.nypl.simplified.opds.core

import one.irradia.mime.api.MIMEType
import java.io.Serializable
import java.net.URI

/**
 * A OPDS preview acquisition.
 */

data class OPDSPreviewAcquisition(

  /**
   * The URI of the preview acquisition
   */

  val uri: URI,

  /**
   * The MIME type of immediately retrievable content, if any.
   */

  val type: MIMEType,

) : Serializable
