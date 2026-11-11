package org.nypl.simplified.feeds.api

/**
 * A feed category.
 */
data class FeedCategory(
  val term: String,
  val scheme: String,
  val label: String?,
) {
  /**
   * The label, or if one is not defined, the term.
   */
  val effectiveLabel: String
    get() = this.label ?: this.term
}
