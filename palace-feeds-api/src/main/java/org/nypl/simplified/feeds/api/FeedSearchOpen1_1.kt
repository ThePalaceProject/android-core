package org.nypl.simplified.feeds.api

import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/**
 * The search should be performed via an OpenSearch 1.1 REST API.
 */
data class FeedSearchOpen1_1(
  private val queryFn: (String) -> URI,
) : FeedSearch {
  /**
   * @param terms The search terms.
   * @return A query URI for searching with the given search terms.
   */
  fun getQueryURIForTerms(terms: String): URI = this.queryFn(terms)

  companion object {
    /**
     * Create a search from a URL template containing `{searchTerms}`.
     */
    fun fromTemplate(template: String): FeedSearchOpen1_1 {
      val fn: (String) -> URI = { terms ->
        try {
          val encoded = URLEncoder.encode(terms, "UTF-8")
          val raw = template.replace("{searchTerms}", encoded)
          URI(raw)
        } catch (e: UnsupportedEncodingException) {
          throw IllegalStateException("UTF-8 is always supported", e)
        } catch (e: URISyntaxException) {
          throw IllegalStateException("Invalid URI from template: $template", e)
        }
      }
      return FeedSearchOpen1_1(fn)
    }
  }
}
