package org.nypl.simplified.feeds.opds12

import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedEntrySerializerType
import org.nypl.simplified.opds.core.OPDSJSONSerializerType
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * A [FeedEntrySerializerType] implementation that uses the OPDS 1.2 JSON
 * serializer. The output format is byte-identical to the previous
 * `OPDSJSONSerializer`-based serialization.
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
