package org.nypl.simplified.opds.core

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.IOException
import java.io.OutputStream

/**
 * The type of serializers that produce simple JSON in a private format from
 * OPDS feeds.
 */

interface OPDSJSONSerializerType {
  /**
   * Serialize the given feed to JSON.
   *
   * @param e The feed
   * @return JSON
   * @throws OPDSSerializationException On serialization errors
   */
  @Throws(OPDSSerializationException::class)
  fun serializeFeed(e: OPDSAcquisitionFeed): ObjectNode

  /**
   * Serialize the given feed entry to JSON.
   *
   * @param e The feed entry
   * @return JSON
   * @throws OPDSSerializationException On serialization errors
   */
  @Throws(OPDSSerializationException::class)
  fun serializeFeedEntry(e: OPDSAcquisitionFeedEntry): ObjectNode

  /**
   * Serialize the given availability type to JSON.
   *
   * @param a The availability type
   * @return JSON
   */
  fun serializeAvailability(a: OPDSAvailabilityType): ObjectNode

  /**
   * Serialize the given acquisition to JSON.
   *
   * @param a The acquisition
   * @return JSON
   */
  @Throws(OPDSSerializationException::class)
  fun serializeAcquisition(a: OPDSAcquisition): ObjectNode

  /**
   * Serialize the given preview acquisition to JSON.
   *
   * @param a The preview acquisition
   * @return JSON
   */
  @Throws(OPDSSerializationException::class)
  fun serializePreviewAcquisition(a: OPDSPreviewAcquisition): ObjectNode

  /**
   * Serialize the given category to JSON.
   *
   * @param c The category
   * @return JSON
   */
  fun serializeCategory(c: OPDSCategory): ObjectNode

  /**
   * @param l the licensor
   * @return JSON
   */
  fun serializeLicensor(l: DRMLicensor): ObjectNode

  /**
   * Serialize the given list of indirect acquisitions.
   *
   * @param indirects The indirect acquisitions
   * @return JSON
   * @throws OPDSSerializationException On errors
   */
  @Throws(OPDSSerializationException::class)
  fun serializeIndirectAcquisitions(indirects: List<OPDSIndirectAcquisition>): ArrayNode

  /**
   * Serialize the given indirect acquisition.
   *
   * @param indirect The indirect acquisition
   * @return JSON
   * @throws OPDSSerializationException On errors
   */
  @Throws(OPDSSerializationException::class)
  fun serializeIndirectAcquisition(indirect: OPDSIndirectAcquisition): ObjectNode

  /**
   * Serialize the given JSON to the given output stream.
   *
   * @param d  The JSON
   * @param os The output stream
   * @throws IOException On I/O errors
   */
  @Throws(IOException::class)
  fun serializeToStream(
    d: ObjectNode,
    os: OutputStream
  )
}
