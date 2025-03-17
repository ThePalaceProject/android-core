package org.nypl.simplified.ui.accounts

import java.io.Serializable

/**
 * Parameters for the account card creator screen.
 */

class AccountCardCreatorParameters(
  val url: String,
  val lat: Double,
  val long: Double
) : Serializable
