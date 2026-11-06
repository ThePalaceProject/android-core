package org.nypl.simplified.feeds.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Functions to construct feed book IDs.
 */
public final class FeedBookIDs {

  private FeedBookIDs() {

  }

  /**
   * Construct a feed book ID derived from the hash of the given text.
   *
   * @param text The text
   * @return A new feed book ID
   */
  public static FeedBookID newFromText(String text) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(text.getBytes());
      byte[] dg = md.digest();

      StringBuilder b = new StringBuilder(64);
      for (byte bb : dg) {
        b.append(String.format("%02x", bb));
      }

      return FeedBookID.create(b.toString());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
