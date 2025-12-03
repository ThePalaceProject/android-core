package org.thepalaceproject.db.internal

import one.irradia.mime.vanilla.MIMEParser
import org.joda.time.DateTime
import org.nypl.simplified.accounts.api.AccountDistance
import org.nypl.simplified.accounts.api.AccountDistanceUnit
import org.nypl.simplified.accounts.api.AccountGeoLocation
import org.nypl.simplified.accounts.api.AccountLibraryLocation
import org.nypl.simplified.accounts.api.AccountProviderDescription
import org.nypl.simplified.links.Link
import org.thepalaceproject.db.internal.generated.DBSerialization
import java.net.URI

internal object DBAccountProviderDescriptionsProtobuf {

  private val parser =
    DBSerialization.DBSAccountProviderDescription1.parser()

  fun descriptionFromP1Bytes(
    bytes: ByteArray
  ): AccountProviderDescription {
    return this.descriptionFromP1(this.parser.parseFrom(bytes))
  }

  private fun descriptionFromP1(
    src: DBSerialization.DBSAccountProviderDescription1
  ): AccountProviderDescription {
    val location =
      if (src.hasLocation()) {
        this.locationFromP1(src.location)
      } else {
        null
      }

    return AccountProviderDescription(
      id = URI.create(src.id),
      title = src.title,
      description = src.description,
      updated = DateTime.parse(src.updated),
      links = src.linksList.map { x -> this.linkFromP1(x) },
      images = src.imagesList.map { x -> this.linkFromP1(x) },
      isAutomatic = src.isAutomatic,
      isProduction = src.isProduction,
      location = location
    )
  }

  fun linkFromP1(
    src: DBSerialization.DBSLink1
  ): Link {
    val type = if (src.type.isNotEmpty()) {
      MIMEParser.parseRaisingException(src.type)
    } else {
      null
    }

    if (src.templated) {
      return Link.LinkTemplated(
        href = src.href,
        type = type,
        relation = src.relation,
        title = src.title,
        height = src.height,
        width = src.width,
        duration = src.duration,
        bitrate = src.bitrate
      )
    } else {
      return Link.LinkBasic(
        href = URI.create(src.href),
        type = type,
        relation = src.relation,
        title = src.title,
        height = src.height,
        width = src.width,
        duration = src.duration,
        bitrate = src.bitrate
      )
    }
  }

  fun locationFromP1(
    src: DBSerialization.DBSAccountLibraryLocation1?
  ): AccountLibraryLocation? {
    if (src == null) {
      return null
    }
    return AccountLibraryLocation(
      location = this.geoLocationFromP1(src.location),
      distance = this.distanceFromP1(src.distance)
    )
  }

  fun distanceFromP1(
    src: DBSerialization.DBSAccountDistance1?
  ): AccountDistance? {
    if (src == null) {
      return null
    }
    return AccountDistance(
      length = src.length,
      unit = this.unitFromP1(src.unit)
    )
  }

  fun unitFromP1(
    src: DBSerialization.DBSAccountDistanceUnit1?
  ): AccountDistanceUnit {
    return when (src) {
      DBSerialization.DBSAccountDistanceUnit1.KILOMETERS -> AccountDistanceUnit.KILOMETERS
      DBSerialization.DBSAccountDistanceUnit1.UNRECOGNIZED -> AccountDistanceUnit.KILOMETERS
      null -> AccountDistanceUnit.KILOMETERS
    }
  }

  fun geoLocationFromP1(
    src: DBSerialization.DBSAccountGeoLocation1
  ): AccountGeoLocation {
    return AccountGeoLocation.Coordinates(
      longitude = src.longitude,
      latitude = src.latitude
    )
  }

  fun descriptionToP1Bytes(
    description: AccountProviderDescription
  ): ByteArray {
    return this.descriptionToP1(description).toByteArray()
  }

  fun descriptionToP1(
    description: AccountProviderDescription
  ): DBSerialization.DBSAccountProviderDescription1 {
    val builder = DBSerialization.DBSAccountProviderDescription1.newBuilder()
    builder.description = description.description
    builder.title = description.title
    builder.id = description.id.toString()
    builder.updated = description.updated.toString()
    builder.isProduction = description.isProduction
    builder.isAutomatic = description.isAutomatic

    for (link in description.links) {
      builder.addLinks(this.linkToP1(link))
    }
    for (link in description.images) {
      builder.addImages(this.linkToP1(link))
    }
    val r = builder.build()
    return r
  }

  fun linkToP1(
    link: Link
  ): DBSerialization.DBSLink1 {
    val builder = DBSerialization.DBSLink1.newBuilder()
    link.bitrate?.let { x -> builder.bitrate = x }
    link.duration?.let { x -> builder.duration = x }
    link.height?.let { x -> builder.height = x }
    link.relation?.let { x -> builder.relation = x }
    link.title?.let { x -> builder.title = x }
    link.type?.let { x -> builder.type = x.fullType }

    return when (link) {
      is Link.LinkBasic -> {
        builder.href = link.href.toString()
        builder.templated = false
        builder.build()
      }

      is Link.LinkTemplated -> {
        builder.href = link.href
        builder.templated = true
        builder.build()
      }
    }
  }
}
