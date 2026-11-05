package org.nypl.simplified.feeds.api

/**
 * DRM licensor information.
 */
data class FeedDRMLicensor(
  val vendor: String,
  val clientToken: String,
  val deviceManager: String?
) : java.io.Serializable
