package org.nypl.simplified.feeds.opds12

import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedEntrySerializerType
import org.nypl.simplified.opds.core.OPDSJSONSerializerType
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * A [FeedEntrySerializerType] implementation that serializes a [FeedEntryBook]
 * to an OPDS 1.2 JSON document.
 */
class FeedEntrySerializer(
  private val opdsSerializer: OPDSJSONSerializerType,
) : FeedEntrySerializerType {
  @Throws(IOException::class)
  override fun serializeEntry(entry: FeedEntryBook): String {
    val objectNode =
      this.opdsSerializer.serializeFeedEntry(
        FeedEntryBookToOPDS.toOPDSAcquisitionFeedEntry(entry),
      )
    val baos = ByteArrayOutputStream()
    this.opdsSerializer.serializeToStream(objectNode, baos)
    return baos.toString(Charsets.UTF_8)
  }
}
