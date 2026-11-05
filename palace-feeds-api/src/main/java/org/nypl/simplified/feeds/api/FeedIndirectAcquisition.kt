package org.nypl.simplified.feeds.api

import one.irradia.mime.api.MIMEType

/**
 * A tree of indirect acquisitions.
 */
data class FeedIndirectAcquisition(
  val type: MIMEType,
  val indirectAcquisitions: List<FeedIndirectAcquisition>,
  val properties: Map<String, String>
): java.io.Serializable {
  /**
   * Find an indirect acquisition with the given type.
   *
   * @return The acquisition, or null if no acquisition exists with the given type.
   */
  fun findType(wantType: MIMEType): FeedIndirectAcquisition? {
    if (this.type.fullType == wantType.fullType) {
      return this
    }
    for (child in this.indirectAcquisitions) {
      val target = child.findType(wantType)
      if (target != null) {
        return target
      }
    }
    return null
  }

  companion object {
    /**
     * Find an indirect acquisition with the given type from a list.
     *
     * @return The acquisition, or null if no acquisition exists with the given type.
     */
    fun findTypeIn(
      wantType: MIMEType,
      indirects: List<FeedIndirectAcquisition>
    ): FeedIndirectAcquisition? = indirects.find { indirect -> indirect.findType(wantType) != null }

    /**
     * @return The set of final content types accessible if all acquisitions are followed to their conclusions.
     */
    fun availableFinalContentTypesIn(indirects: List<FeedIndirectAcquisition>): Set<MIMEType> {
      val types = mutableSetOf<MIMEType>()
      for (indirect in indirects) {
        if (indirect.indirectAcquisitions.isEmpty()) {
          types.add(indirect.type)
        } else {
          types.addAll(availableFinalContentTypesIn(indirect.indirectAcquisitions))
        }
      }
      return types
    }
  }
}
