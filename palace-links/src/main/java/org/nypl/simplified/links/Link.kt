package org.nypl.simplified.links

import one.irradia.mime.api.MIMEType
import java.io.Serializable
import java.net.URI

/**
 * A link.
 */

sealed class Link : Serializable {

  /**
   * The MIME type of the link content.
   */

  abstract val type: MIMEType?

  /**
   * The relation of the link.
   */

  abstract val relation: String?

  /**
   * Title of the linked resource
   */

  abstract val title: String?

  /**
   * Height of the linked resource in pixels
   */

  abstract val height: Int?

  /**
   * Width of the linked resource in pixels
   */

  abstract val width: Int?

  /**
   * Duration of the linked resource in seconds
   */

  abstract val duration: Double?

  /**
   * Bit rate of the linked resource in kilobits per second
   */

  abstract val bitrate: Double?

  /**
   * The link target as a URI, if the target is directly expressible as one
   */

  abstract val hrefURI: URI?

  /**
   * Cast the current link to a basic link, failing with an appropriate error message if this
   * link is not a basic link.
   */

  abstract fun toBasic(): LinkBasic

  /**
   * Cast the current link to a templated link, failing with an appropriate error message if this
   * link is not a templated link.
   */

  abstract fun toTemplated(): LinkTemplated

  /**
   * A non-templated, basic link.
   */

  data class LinkBasic(
    val href: URI,
    override val type: MIMEType? = null,
    override val relation: String? = null,
    override val title: String? = null,
    override val height: Int? = null,
    override val width: Int? = null,
    override val duration: Double? = null,
    override val bitrate: Double? = null
  ) : Link(), Serializable {
    override val hrefURI: URI
      get() = this.href

    override fun toBasic(): LinkBasic {
      return this
    }

    override fun toTemplated(): LinkTemplated {
      throw IllegalArgumentException("Expected a templated link, but this link is a basic link.")
    }
  }

  /**
   * A templated link.
   */

  data class LinkTemplated(
    val href: String,
    override val type: MIMEType? = null,
    override val relation: String? = null,
    override val title: String? = null,
    override val height: Int? = null,
    override val width: Int? = null,
    override val duration: Double? = null,
    override val bitrate: Double? = null
  ) : Link(), Serializable {
    override val hrefURI: URI?
      get() = null

    override fun toBasic(): LinkBasic {
      throw IllegalArgumentException("Expected a basic link, but this link is a templated link.")
    }

    override fun toTemplated(): LinkTemplated {
      return this
    }
  }
}
