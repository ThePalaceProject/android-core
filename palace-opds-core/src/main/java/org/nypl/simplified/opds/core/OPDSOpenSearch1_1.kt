package org.nypl.simplified.opds.core

import com.io7m.junreachable.UnreachableCodeException
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder

/**
 * The type of Open Search 1.1 descriptions.
 *
 * @see [OpenSearch 1.1](http://www.opensearch.org/Specifications/OpenSearch/1.1)
 */

data class OPDSOpenSearch1_1(
  private val template: String
) {
  /**
   * @param terms The search terms
   * @return A query URI for searching with the given search terms
   */

  fun getQueryURIForTerms(terms: String): URI {
    try {
      val encoded = URLEncoder.encode(terms, "UTF-8")
      val raw = this.template.replace("{searchTerms}", encoded)
      return URI(raw)
    } catch (e: UnsupportedEncodingException) {
      throw UnreachableCodeException(e)
    } catch (e: URISyntaxException) {
      throw UnreachableCodeException(e)
    }
  }
}
