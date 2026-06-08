package org.nypl.simplified.opds.core

import java.net.URI

/**
 * An OPDS "series" element. This is a Palace extension.
 *
 * ```
 * <schema:series name="Private">
 *   <position>6</position>
 *   <link href="https://gorgon.staging.palaceproject.io/a1qa-test/works/series/Private/eng/" rel="series" type="application/atom+xml;profile=opds-catalog;kind=acquisition" title="Private"/>
 * </schema:series>
 * ```
 */

data class OPDSSeries(
  val name: String,
  val position: Int?,
  val link: URI
)
