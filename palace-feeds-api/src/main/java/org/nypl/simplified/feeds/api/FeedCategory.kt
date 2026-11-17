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
    get() {
      val label = this.label
      if (label == null) {
        return this.term
      }
      return label
    }
}
