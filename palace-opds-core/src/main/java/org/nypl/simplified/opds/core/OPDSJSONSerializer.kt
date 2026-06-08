package org.nypl.simplified.opds.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import one.irradia.mime.api.MIMEType
import org.joda.time.format.ISODateTimeFormat
import org.nypl.simplified.json.core.JSONSerializerUtilities.serialize
import org.nypl.simplified.links.Link
import org.nypl.simplified.links.json.LinkSerialization.serializeLink
import java.io.IOException
import java.io.OutputStream
import java.net.URI

/**
 * The default implementation of the [OPDSJSONSerializerType] interface.
 */
class OPDSJSONSerializer private constructor() : OPDSJSONSerializerType {

  @Throws(OPDSSerializationException::class)
  override fun serializeAcquisition(
    a: OPDSAcquisition
  ): ObjectNode {
    val jom = ObjectMapper()
    val node = jom.createObjectNode()
    node.put("type", a.relation.toString())

    when (a.uri) {
      is Link.LinkBasic -> {
        node.set("linkBasic", serializeLink(a.uri))
      }

      is Link.LinkTemplated -> {
        node.set<JsonNode?>("linkTemplated", serializeLink(a.uri))
      }
    }

    node.put("content_type", serializeContentType(a.type))
    node.put("properties", serializeProperties(a.properties))
    node.set<JsonNode?>(
      "indirect_acquisitions",
      serializeIndirectAcquisitions(a.indirectAcquisitions)
    )
    return node
  }

  @Throws(OPDSSerializationException::class)
  override fun serializePreviewAcquisition(
    a: OPDSPreviewAcquisition
  ): ObjectNode {
    val jom = ObjectMapper()
    val node = jom.createObjectNode()
    node.put("type", a.type.toString())
    node.put("uri", a.uri.toString())
    return node
  }

  private fun serializeContentType(mimeType: MIMEType): String {
    val parameters: Map<String, String> = mimeType.parameters

    if (parameters.isEmpty()) {
      return mimeType.fullType
    } else {
      val stringBuilder = StringBuilder()
      stringBuilder.append(mimeType.fullType)
      for (key in parameters.keys) {
        stringBuilder.append(";").append(key).append("=").append(parameters.get(key))
      }

      return stringBuilder.toString()
    }
  }

  private fun serializeProperties(
    properties: Map<String, String>
  ): ObjectNode {
    val jom = ObjectMapper()
    val node = jom.createObjectNode()
    for (entry in properties.entries) {
      node.put(entry.key, entry.value)
    }
    return node
  }

  @Throws(OPDSSerializationException::class)
  override fun serializeIndirectAcquisitions(
    indirects: List<OPDSIndirectAcquisition>
  ): ArrayNode {
    val jom = ObjectMapper()
    val node = jom.createArrayNode()

    for (indirect in indirects) {
      node.add(serializeIndirectAcquisition(indirect))
    }
    return node
  }

  @Throws(OPDSSerializationException::class)
  override fun serializeIndirectAcquisition(
    indirect: OPDSIndirectAcquisition
  ): ObjectNode {
    val jom = ObjectMapper()
    val node = jom.createObjectNode()

    node.put("type", indirect.type.fullType)
    node.set<JsonNode?>(
      "indirect_acquisitions",
      serializeIndirectAcquisitions(indirect.indirectAcquisitions)
    )
    node.put("properties", serializeProperties(indirect.properties))
    return node
  }

  override fun serializeAvailability(
    a: OPDSAvailabilityType
  ): ObjectNode {
    val fmt = ISODateTimeFormat.dateTime()
    val jom = ObjectMapper()

    return when (a) {
      is OPDSAvailabilityHeld -> {
        val o: ObjectNode = jom.createObjectNode()
        val oh: ObjectNode = jom.createObjectNode()
        a.startDate.let({ t ->
          oh.put("start_date", fmt.print(t))
        })
        a.position.let({ x ->
          oh.put("position", x)
        })
        a.revoke.let({ uri ->
          oh.put("revoke", uri.toString())
        })
        o.set<JsonNode?>("held", oh)
        o
      }

      is OPDSAvailabilityHeldReady -> {
        val o: ObjectNode = jom.createObjectNode()
        val oh: ObjectNode = jom.createObjectNode()
        a.endDate.let({ t ->
          oh.put("end_date", fmt.print(t))
        })
        a.revoke.let({ uri ->
          oh.put("revoke", uri.toString())
        })
        o.set<JsonNode?>("held_ready", oh)
        o
      }

      is OPDSAvailabilityLoaned -> {
        val o: ObjectNode = jom.createObjectNode()
        val oh: ObjectNode = jom.createObjectNode()
        a.startDate.let({ t ->
          oh.put("start_date", fmt.print(t))
        })
        a.endDate.let({ t ->
          oh.put("end_date", fmt.print(t))
        })
        a.revoke.let({ uri ->
          oh.put("revoke", uri.toString())
        })
        o.set<JsonNode?>("loaned", oh)
        o
      }

      is OPDSAvailabilityOpenAccess -> {
        val o: ObjectNode = jom.createObjectNode()
        val oh: ObjectNode = jom.createObjectNode()
        a.revoke.let(
          { uri ->
            oh.put("revoke", uri.toString())
          })
        o.set<JsonNode?>("open_access", oh)
        o
      }

      is OPDSAvailabilityRevoked -> {
        val o: ObjectNode = jom.createObjectNode()
        val oh: ObjectNode = jom.createObjectNode()
        oh.put("revoke", a.revoke.toString())
        o.set<JsonNode?>("revoked", oh)
        return o
      }

      is OPDSAvailabilityHoldable -> {
        val o: ObjectNode = jom.createObjectNode()
        val oh: ObjectNode = jom.createObjectNode()
        o.set<JsonNode?>("holdable", oh)
        o
      }

      is OPDSAvailabilityLoanable -> {
        val o: ObjectNode = jom.createObjectNode()
        val oh: ObjectNode = jom.createObjectNode()
        o.set<JsonNode?>("loanable", oh)
        o
      }
    }
  }

  override fun serializeCategory(
    c: OPDSCategory
  ): ObjectNode {
    val jom = ObjectMapper()
    val je = jom.createObjectNode()
    je.put("scheme", c.scheme)
    je.put("term", c.term)

    val label: String? = c.label
    if (label != null) {
      je.put("label", label)
    }
    return je
  }

  override fun serializeLicensor(l: DRMLicensor): ObjectNode {
    val jom = ObjectMapper()
    val je = jom.createObjectNode()
    je.put("vendor", l.vendor)
    je.put("clientToken", l.clientToken)

    if (l.deviceManager != null) {
      je.put("deviceManager", l.deviceManager)
    }

    return je
  }

  @Throws(OPDSSerializationException::class)
  override fun serializeFeedEntry(
    e: OPDSAcquisitionFeedEntry
  ): ObjectNode {
    val jom = ObjectMapper()
    val je = jom.createObjectNode()
    val fmt = ISODateTimeFormat.dateTime()

    run {
      val ja = jom.createArrayNode()
      for (a in e.authors) {
        ja.add(a)
      }
      je.set<JsonNode>("authors", ja)
    }

    run {
      val ja = jom.createArrayNode()
      for (a in e.acquisitions) {
        ja.add(this.serializeAcquisition(a))
      }
      je.set<JsonNode>("acquisitions", ja)
    }

    run {
      val ja = jom.createArrayNode()
      for (a in e.previewAcquisitions) {
        ja.add(this.serializePreviewAcquisition(a))
      }
      je.set<JsonNode>("previews", ja)
    }

    run {
      je.set<JsonNode>("availability", this.serializeAvailability(e.availability))
    }

    run {
      if (e.licensor != null) {
        je.set<JsonNode>("licensor", this.serializeLicensor(e.licensor))
      }
    }

    run {
      val ja = jom.createArrayNode()
      for (c in e.categories) {
        ja.add(this.serializeCategory(c))
      }
      je.set<JsonNode>("categories", ja)
    }

    e.cover.let({ u ->
      je.put("cover", u.toString())
    })

    run {
      val eg: Set<Pair<String, URI>> = e.groups
      val a = jom.createArrayNode()
      for (p in eg) {
        val o = jom.createObjectNode()
        o.put("name", p.first)
        o.put("uri", p.second.toString())
        a.add(o)
      }
      je.set<JsonNode>("groups", a)
    }

    je.put("id", e.id)

    e.published.let({ c ->
      je.put("published", fmt.print(c))
    })
    e.publisher.let({ s ->
      je.put("publisher", s)
    })

    je.put("distribution", e.distribution)
    je.put("summary", e.summary)
    je.put("title", e.title)

    e.thumbnail.let({ u ->
      je.put("thumbnail", u.toString())
    })

    e.alternate.let({ u ->
      je.put("alternate", u.toString())
      je.put("analytics", u.toString().replace("/works/", "/analytics/"))
    })

    e.annotations.let(
      { u ->
        je.put("annotations", u.toString())
      })

    je.put("updated", fmt.print(e.updated))
    return je
  }

  @Throws(OPDSSerializationException::class)
  override fun serializeFeed(
    e: OPDSAcquisitionFeed
  ): ObjectNode {
    val jom = ObjectMapper()
    val je = jom.createObjectNode()
    val fmt = ISODateTimeFormat.dateTime()

    je.put("id", e.feedID)
    je.put("title", e.feedTitle)

    e.feedNext.let({ next ->
      je.put("next", next.toString())
    })

    run {
      val a = jom.createArrayNode()
      for (k in e.feedFacetsOrder) {
        val o = jom.createObjectNode()
        o.put("group", k.group)
        o.put("active", k.isActive)
        o.put("title", k.title)
        o.put("uri", k.uri.toString())
        k.groupType?.let { type -> o.put("group_type", type) }
        a.add(o)
      }
      je.set<JsonNode>("facets", a)
    }

    run {
      val a = jom.createArrayNode()
      for (fe in e.feedEntries) {
        a.add(this.serializeFeedEntry(fe))
      }
      je.set<JsonNode>("entries", a)
    }

    e.feedSearchURI?.let { s ->
      val os = jom.createObjectNode()
      os.put("type", s.type)
      os.put("uri", s.uri.toString())
      je.set<JsonNode>("search", os)
    }

    je.put("updated", fmt.print(e.feedUpdated))
    je.put("uri", e.feedURI.toString())
    return je
  }

  @Throws(IOException::class)
  override fun serializeToStream(
    d: ObjectNode,
    os: OutputStream
  ) {
    serialize(d, os)
  }

  companion object {
    /**
     * @return A new JSON serializer
     */
    @JvmStatic
    fun newSerializer(): OPDSJSONSerializerType {
      return OPDSJSONSerializer()
    }
  }
}
