package org.nypl.simplified.ui.main;

import android.content.res.AssetManager;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.books.bundled.api.BundledContentResolverType;
import org.nypl.simplified.books.bundled.api.BundledURIs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * An implementation of the bundled content resolver based on Android assets.
 */

public final class MainBundledContentResolver implements BundledContentResolverType {

  private static final Logger LOG = LoggerFactory.getLogger(MainBundledContentResolver.class);

  private final AssetManager assets;

  private MainBundledContentResolver(
      final AssetManager assets)
  {
    this.assets = NullCheck.notNull(assets, "assets");
  }

  public static BundledContentResolverType create(
      final AssetManager assets)
  {
    return new MainBundledContentResolver(assets);
  }

  @Override
  public InputStream resolve(final URI uri) throws IOException {
    return stream(uri);
  }

  private InputStream stream(final URI uri) throws IOException {
    NullCheck.notNull(uri, "uri");

    LOG.debug("resolve: {}", uri);

    if (!BundledURIs.isBundledURI(uri)) {
      throw new IOException("Not a bundled URI: " + uri);
    }

    final String path = uri.getSchemeSpecificPart()
        .replaceFirst("^[/]+", "");

    LOG.debug("path: {}", path);
    return this.assets.open(path);
  }
}
