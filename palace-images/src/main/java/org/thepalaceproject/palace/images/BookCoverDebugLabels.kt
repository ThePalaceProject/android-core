package org.thepalaceproject.palace.images

/**
 * The debug-only text labels to overlay on a book cover: the book format
 * (e.g. "epub") and the DRM system (e.g. "Adobe").
 *
 * These are intended to make it possible to quickly identify books with a
 * given format/DRM combination in the catalog grid without opening the book.
 * They are only rendered in debug builds.
 */

data class BookCoverDebugLabels(
  val format: String,
  val drm: String
)
