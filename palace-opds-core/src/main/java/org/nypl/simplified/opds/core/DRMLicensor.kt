package org.nypl.simplified.opds.core

import java.io.Serializable

/**
 * DRM licensor information.
 *
 * @property vendor The vendor
 * @property clientToken The client token
 * @property deviceManager The device manager URL
 */
data class DRMLicensor(

  /**
   * The vendor.
   */
  val vendor: String,

  /**
   * The client token.
   */
  val clientToken: String,

  /**
   * The device manager URL.
   */
  val deviceManager: String?

) : Serializable {

  companion object {
    private const val serialVersionUID = 1L
  }
}
