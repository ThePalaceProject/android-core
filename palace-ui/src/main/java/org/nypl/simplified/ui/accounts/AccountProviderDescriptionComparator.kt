package org.nypl.simplified.ui.accounts

import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import java.net.URI

/**
 * A comparator that implements the "featured libraries" aspect of a list. A featured library
 * is always pushed to the start of a sorted list so that it can appear at the top of results
 * in the way lists are normally displayed.
 */

class AccountProviderDescriptionComparator(
  val buildConfig: BuildConfigurationServiceType
) : Comparator<AccountProviderDescription> {

  private val featuredIds =
    try {
      this.buildConfig.featuredLibrariesIdsList.map { x -> URI.create(x) }
    } catch (e: Throwable) {
      setOf<URI>()
    }

  private fun isFeatured(
    d: AccountProviderDescription
  ): Boolean {
    return this.featuredIds.contains(d.id)
  }

  override fun compare(
    o1: AccountProviderDescription,
    o2: AccountProviderDescription
  ): Int {
    return if (this.isFeatured(o1)) {
      if (this.isFeatured(o2)) {
        o1.title.uppercase().compareTo(o2.title.uppercase())
      } else {
        -1
      }
    } else {
      if (this.isFeatured(o2)) {
        1
      } else {
        o1.title.uppercase().compareTo(o2.title.uppercase())
      }
    }
  }
}
