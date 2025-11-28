package org.thepalaceproject.opds.client

import org.nypl.simplified.feeds.api.FeedLoaderType

/**
 * The parameters required to instantiate an OPDS client.
 */

data class OPDSClientParameters(
  val name: String,
  val runOnUI: (Runnable) -> Unit,
  val checkOnUI: () -> Unit,
  val feedLoader: FeedLoaderType
)
