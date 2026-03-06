package org.nypl.simplified.profiles.api

/**
 * A description of a profile.
 */

data class ProfileDescription(
  val preferences: ProfilePreferences,
  val attributes: ProfileAttributes
)
