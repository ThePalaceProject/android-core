package org.nypl.simplified.links

import org.thepalaceproject.webpub.core.WPMLink
import org.thepalaceproject.webpub.core.WPMLinkBasic
import org.thepalaceproject.webpub.core.WPMLinkTemplated

object Links {

  fun wpmLinkToPalaceLink(
    link: WPMLink
  ): Link {
    return when (link) {
      is WPMLinkBasic -> {
        Link.LinkBasic(
          href = link.href,
          type = link.type,
          relation = link.relation.firstOrNull(),
          title = link.title,
          height = link.height,
          width = link.width,
          duration = link.duration?.toDouble(),
          bitrate = link.bitrate?.toDouble()
        )
      }

      is WPMLinkTemplated -> {
        Link.LinkTemplated(
          href = link.href,
          type = link.type,
          relation = link.relation.firstOrNull(),
          title = link.title,
          height = link.height,
          width = link.width,
          duration = link.duration?.toDouble(),
          bitrate = link.bitrate?.toDouble()
        )
      }
    }
  }
}
