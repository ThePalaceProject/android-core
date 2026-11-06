package org.nypl.simplified.feeds.api;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The unique identifier for a book within a feed. This is typically a SHA256 hash of the
 * original book URI and is intended to be safe for use as a file or directory name.
 */
public final class FeedBookID implements Comparable<FeedBookID>, Serializable {

  /**
   * The regular expression that defines a valid feed book ID.
   */
  public static final Pattern VALID_FEED_BOOK_ID = Pattern.compile("[a-z0-9]+");

  private final String value;

  private FeedBookID(String value) {
    this.value = value;
  }

  /**
   * Construct a feed book ID.
   *
   * @param value The raw feed book ID value
   */
  public static FeedBookID create(String value) {
    if (!VALID_FEED_BOOK_ID.matcher(value).matches()) {
      throw new IllegalArgumentException(
        "Feed book IDs must be non-empty, alphanumeric lowercase (" + VALID_FEED_BOOK_ID.pattern() + ")");
    }
    return new FeedBookID(value);
  }

  /**
   * @return The actual ID value
   */
  public String value() {
    return this.value;
  }

  /**
   * @return The brief form of the ID
   */
  public String brief() {
    return this.value.substring(0, Math.min(8, this.value.length()));
  }

  @Override
  public String toString() {
    return this.value;
  }

  @Override
  public final int compareTo(FeedBookID other) {
    return this.value.compareTo(Objects.requireNonNull(other, "Other").value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FeedBookID that = (FeedBookID) o;
    return value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }
}
