package org.nypl.simplified.opds.core

import java.io.InputStream
import java.net.URI

/**
 * The type of parsers that consume [InputStream] values and produce
 * search descriptions.
 *
 * Implementations are required to be able to accept requests from any number
 * of threads simultaneously.
 */

interface OPDSSearchParserType {
  /**
   * Parse the search description associated with the given stream
   * `s`. The description is assumed to exist at `uri`.
   *
   * @param uri The URI of the description
   * @param s   The input stream
   * @return A parsed description
   * @throws OPDSParseException On errors
   */
  @Throws(OPDSParseException::class)
  fun parse(uri: URI, s: InputStream): OPDSOpenSearch1_1
}
