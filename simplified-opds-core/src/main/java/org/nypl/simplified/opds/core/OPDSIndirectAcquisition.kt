package org.nypl.simplified.opds.core

import com.io7m.jfunctional.Option
import com.io7m.jfunctional.OptionType
import one.irradia.mime.api.MIMEType
import java.io.Serializable

/**
 * A tree of indirect acquisitions.
 */

data class OPDSIndirectAcquisition(

  /**
   * The MIME type of the indirectly obtainable content.
   */

  val type: MIMEType,

  /**
   * Zero or more nested indirect acquisitions.
   */

  val indirectAcquisitions: List<OPDSIndirectAcquisition>,

  /**
   * Extra properties associated with the acquisition.
   */

  val properties: Map<String, String>

) : Serializable {

  /**
   * Find an indirect acquisition with the given type.
   *
   * @return The acquisition, or null if no acquisition exists with the given type
   */

  fun findType(wantType: MIMEType): OPDSIndirectAcquisition? {
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

  /**
   * Find an indirect acquisition with the given type.
   *
   * @return The acquisition, or `None` if no acquisition exists with the given type
   */

  fun findTypeOptional(wantType: MIMEType): OptionType<OPDSIndirectAcquisition> {
    return Option.of(this.findType(wantType))
  }

  companion object {

    /**
     * Find an indirect acquisition with the given type.
     *
     * @return The acquisition, or null if no acquisition exists with the given type
     */

    fun findTypeIn(
      wantType: MIMEType,
      indirects: List<OPDSIndirectAcquisition>
    ): OPDSIndirectAcquisition? {
      return indirects.find { indirect -> indirect.findType(wantType) != null }
    }

    /**
     * Find an indirect acquisition with the given type.
     *
     * @return The acquisition, or `None` if no acquisition exists with the given type
     */

    fun findTypeInOptional(
      wantType: MIMEType,
      indirects: List<OPDSIndirectAcquisition>
    ): OptionType<OPDSIndirectAcquisition> {
      return Option.of(this.findTypeIn(wantType, indirects))
    }

    /**
     * @return The set of final content types. That is, the set of content types that are accessible
     * if all acquisitions are followed to their conclusions
     */

    fun availableFinalContentTypesIn(
      indirects: List<OPDSIndirectAcquisition>
    ): Collection<MIMEType> {
      val types = mutableSetOf<MIMEType>()

      for (indirect in indirects) {
        if (indirect.indirectAcquisitions.isEmpty()) {
          types.add(indirect.type)
        } else {
          types.addAll(this.availableFinalContentTypesIn(indirect.indirectAcquisitions))
        }
      }

      return types
    }
  }
}
