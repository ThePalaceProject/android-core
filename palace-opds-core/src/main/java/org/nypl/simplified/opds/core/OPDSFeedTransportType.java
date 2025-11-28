package org.nypl.simplified.opds.core;

import java.io.InputStream;
import java.net.URI;

import kotlin.Pair;

/**
 * A mapping from {@link URI} to {@link InputStream}.
 *
 * Mostly included to facilitate unit testing without needing an HTTP server to
 * exist.
 *
 * @param <A> Implementation specific per-URI context data
 */

public interface OPDSFeedTransportType<A>
{
  /**
   * @param context Implementation-specific per-URI context data
   * @param uri     The URI
   * @param method  HTTP method to use (GET/PUT)
   *
   * @return An input stream for the given URI.
   *
   * @throws OPDSFeedTransportException On errors
   */

  Pair<InputStream, String> getStream(
    final A context,
    final URI uri,
    final String method)
    throws OPDSFeedTransportException;
}
