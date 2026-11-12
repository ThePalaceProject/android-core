package org.nypl.simplified.feeds.api

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/**
 * The search should be performed via an OpenSearch 1.1 REST API.
 */
data class FeedSearchOpen1_1 private constructor(
  private val template: String,
) : FeedSearch {
  /**
   * Construct the query URI for the given search terms. The terms are form-encoded before
   * being substituted into the template.
   *
   * @param terms The search terms.
   * @return A query URI for searching with the given search terms.
   */
  fun getQueryURIForTerms(terms: String): URI {
    try {
      val encoded = URLEncoder.encode(terms, "UTF-8")
      val raw = this.template.replace("{searchTerms}", encoded)
      return URI(raw)
    } catch (e: UnsupportedEncodingException) {
      throw IllegalStateException("UTF-8 is always supported", e)
    } catch (e: URISyntaxException) {
      throw IllegalStateException("Invalid URI from template: $template", e)
    }
  }

  companion object {
    /**
     * Create a search from a URL template containing `{searchTerms}`.
     *
     * Search terms are form-encoded before being substituted into the template.
     */
    fun fromTemplate(template: String): FeedSearchOpen1_1 {
      return FeedSearchOpen1_1(template)
    }
  }
}
