package org.nypl.simplified.accounts.registry.api

import java.net.URI

data class AccountProviderRegistryOverride(
  val hostname: String,
  val path: String,
  val queryParameters: String
) {
  fun completeURI(): URI {
    val b = StringBuilder()
    b.append("https://")
    b.append(this.hostname)
    b.append("/")
    b.append(this.path)
    b.append("?")
    b.append(this.queryParameters)
    return URI.create(b.toString())
  }
}
