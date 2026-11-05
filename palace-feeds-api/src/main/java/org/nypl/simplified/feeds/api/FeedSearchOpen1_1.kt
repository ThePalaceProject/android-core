package org.nypl.simplified.feeds.api

import com.io7m.junreachable.UnreachableCodeException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/**
 * The search should be performed via an OpenSearch 1.1 REST API.
 */
data class FeedSearchOpen1_1(
  val template: String
) : FeedSearch {
  /**
   * @param terms The search terms.
   * @return A query URI for searching with the given search terms.
   */
  fun getQueryURIForTerms(terms: String): URI {
    try {
      val encoded = URLEncoder.encode(terms, "UTF-8")
      val raw = this.template.replace("{searchTerms}", encoded)
      return URI(raw)
    } catch (e: java.io.UnsupportedEncodingException) {
      throw UnreachableCodeException(e)
    } catch (e: URISyntaxException) {
      throw UnreachableCodeException(e)
    }
  }
}
