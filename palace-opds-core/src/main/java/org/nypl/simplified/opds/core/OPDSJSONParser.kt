package org.nypl.simplified.opds.core

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import one.irradia.mime.api.MIMEType
import one.irradia.mime.vanilla.MIMEParser.Companion.parseRaisingException
import org.joda.time.DateTime
import org.nypl.simplified.json.core.JSONParseException
import org.nypl.simplified.json.core.JSONParserUtilities
import org.nypl.simplified.json.core.JSONParserUtilities.checkObject
import org.nypl.simplified.json.core.JSONParserUtilities.checkString
import org.nypl.simplified.json.core.JSONParserUtilities.getArray
import org.nypl.simplified.json.core.JSONParserUtilities.getArrayOrNull
import org.nypl.simplified.json.core.JSONParserUtilities.getBoolean
import org.nypl.simplified.json.core.JSONParserUtilities.getIntegerOrNull
import org.nypl.simplified.json.core.JSONParserUtilities.getNode
import org.nypl.simplified.json.core.JSONParserUtilities.getObject
import org.nypl.simplified.json.core.JSONParserUtilities.getString
import org.nypl.simplified.json.core.JSONParserUtilities.getStringOrNull
import org.nypl.simplified.json.core.JSONParserUtilities.getTimestamp
import org.nypl.simplified.json.core.JSONParserUtilities.getTimestampOrNull
import org.nypl.simplified.json.core.JSONParserUtilities.getURI
import org.nypl.simplified.json.core.JSONParserUtilities.getURIOrNull
import org.nypl.simplified.links.Link
import org.nypl.simplified.links.json.LinkParsing.parseLinkExceptionally
import org.nypl.simplified.opds.core.OPDSAcquisitionFeed.Companion.newBuilder
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException

/**
 * The default implementation of the [OPDSJSONParserType] interface.
 */
class OPDSJSONParser private constructor() : OPDSJSONParserType {
  @Throws(OPDSParseException::class)
  override fun parseAcquisitionFeed(s: ObjectNode): OPDSAcquisitionFeed {
    try {
      val uri = URI(getString(s, "uri"))
      val id = getString(s, "id")
      val updated =
        getTimestamp(
          s, "updated"
        )
      val title = getString(s, "title")

      val fb =
        newBuilder(uri, id, updated, title)

      fb.setNextOption(getURIOrNull(s, "next"))

      fb.setSearchOption(
        JSONParserUtilities.getObjectOrNull(s, "search")?.let { o ->
          val searchType = getString(o, "type")
          val searchURI = getURI(o, "uri")
          OPDSSearchLink(searchType, searchURI)
        }
      )

      run {
        val fs = getArray(s, "facets")
        for (index in 0..<fs.size()) {
          val o = checkObject(null, fs.get(index))
          val facetActive =
            getBoolean(o, "active")
          val facetUri =
            URI(
              getString(o, "uri")
            )
          val facetGroup =
            getString(o, "group")
          val facetGroupType: String? =
            getStringOrNull(o, "group_type")
          val facetTitle =
            getString(o, "title")
          fb.addFacet(
            OPDSFacet(
              facetActive,
              facetUri,
              facetGroup,
              facetTitle,
              facetGroupType
            )
          )
        }
      }

      run {
        val fs = getArray(s, "entries")
        for (index in 0..<fs.size()) {
          val o =
            checkObject(
              null, fs.get(index)
            )
          fb.addEntry(this.parseAcquisitionFeedEntry(o))
        }
      }

      return fb.build()
    } catch (e: URISyntaxException) {
      throw OPDSParseException(e)
    } catch (e: JSONParseException) {
      throw OPDSParseException(e)
    }
  }

  @Throws(OPDSParseException::class)
  override fun parseAcquisitionFeedEntry(s: ObjectNode): OPDSAcquisitionFeedEntry {
    try {
      val id = getString(s, "id")
      val title = getString(s, "title")
      val updated = getTimestamp(s, "updated")

      val availability: OPDSAvailabilityType =
        parseAvailability(
          getObject(s, "availability")
        )

      val fb =
        OPDSAcquisitionFeedEntry.newBuilder(id, title, updated, availability)

      run {
        val a = getArray(s, "authors")
        for (index in 0..<a.size()) {
          fb.addAuthor(a.get(index).asText())
        }
      }

      run {
        val a = getArray(s, "acquisitions")
        for (index in 0..<a.size()) {
          fb.addAcquisition(
            parseAcquisition(
              checkObject(null, a.get(index))
            )
          )
        }
      }

      run {
        if (s.has("licensor")) {
          val a = getNode(s, "licensor")
          fb.setLicensorOption(parseLicensor(a))
        }
      }

      run {
        val a = getArray(s, "categories")
        for (index in 0..<a.size()) {
          fb.addCategory(parseCategory(a.get(index)))
        }
      }

      run {
        val a = getArray(s, "groups")
        for (index in 0..<a.size()) {
          try {
            val jo =
              checkObject(
                null, a.get(index)
              )
            val uri =
              URI(
                getString(
                  jo, "uri"
                )
              )
            val name = getString(jo, "name")
            fb.addGroup(uri, name)
          } catch (e: URISyntaxException) {
            throw OPDSParseException(e)
          }
        }
      }

      fb.setCoverOption(getURIOrNull(s, "cover"))

      run {
        val a = getArrayOrNull(s, "previews")
        if (a != null) {
          for (index in 0..<a.size()) {
            try {
              val jo =
                checkObject(
                  null, a.get(index)
                )
              val uri =
                URI(
                  getString(
                    jo, "uri"
                  )
                )
              val type =
                parseRaisingException(
                  getString(jo, "type")
                )
              fb.addPreviewAcquisition(OPDSPreviewAcquisition(uri, type))
            } catch (e: Exception) {
              throw OPDSParseException(e)
            }
          }
        }
      }

      fb.setThumbnailOption(getURIOrNull(s, "thumbnail"))
      fb.setAlternateOption(getURIOrNull(s, "alternate"))
      fb.setAnalyticsOption(getURIOrNull(s, "analytics"))
      fb.setAnnotationsOption(getURIOrNull(s, "annotations"))
      fb.setPublishedOption(getTimestampOrNull(s, "published"))
      fb.setPublisherOption(getStringOrNull(s, "publisher"))
      fb.setDistribution(getString(s, "distribution"))
      fb.setSummaryOption(getStringOrNull(s, "summary"))
      return fb.build()
    } catch (e: JSONParseException) {
      throw OPDSParseException(e)
    }
  }

  @Throws(OPDSParseException::class)
  override fun parseAcquisitionFeedEntryFromStream(s: InputStream?): OPDSAcquisitionFeedEntry {
    try {
      val jom = ObjectMapper()
      return this.parseAcquisitionFeedEntry(
        checkObject(
          null, jom.readTree(s)
        )
      )
    } catch (e: JsonProcessingException) {
      throw OPDSParseException(e)
    } catch (e: IOException) {
      throw OPDSParseException(e)
    }
  }

  @Throws(OPDSParseException::class)
  override fun parseAcquisitionFeedFromStream(s: InputStream?): OPDSAcquisitionFeed {
    try {
      val jom = ObjectMapper()
      return this.parseAcquisitionFeed(
        checkObject(
          null, jom.readTree(s)
        )
      )
    } catch (e: JsonProcessingException) {
      throw OPDSParseException(e)
    } catch (e: IOException) {
      throw OPDSParseException(e)
    }
  }

  companion object {
    /**
     * The name of the field used for indirect acquisitions.
     */
    const val INDIRECT_ACQUISITIONS_FIELD: String = "indirect_acquisitions"
    const val CONTENT_TYPE_FIELD: String = "content_type"
    const val PROPERTIES_FIELD: String = "properties"

    /**
     * @return A new JSON parser
     */
    @JvmStatic
    fun newParser(): OPDSJSONParserType = OPDSJSONParser()

    @Throws(OPDSParseException::class)
    private fun parseAcquisition(o: ObjectNode): OPDSAcquisition {
      try {
        /*
         * XXX: COMPATIBILITY: this field is called "type" when it should really be called "relation".
         * The name is preserved in order to allow new versions of the application to open old
         * versions of the on-disk data.
         */

        val relation =
          OPDSAcquisition.Relation.valueOf(getString(o, "type"))

        val link: Link =
          findLink(o)

        val indirects: MutableList<OPDSIndirectAcquisition>
        if (o.has(INDIRECT_ACQUISITIONS_FIELD)) {
          indirects =
            parseIndirectAcquisitions(
              getArray(o, INDIRECT_ACQUISITIONS_FIELD)
            )
        } else {
          indirects = mutableListOf()
        }

        /*
         * XXX: COMPATIBILITY: The content type field will not be present for old versions of the
         * book database. Luckily, old book databases can only contain epub files.
         */
        val type: MIMEType?
        if (o.has(CONTENT_TYPE_FIELD)) {
          type =
            parseRaisingException(
              getString(o, CONTENT_TYPE_FIELD)
            )
        } else {
          type = parseRaisingException("application/epub+zip")
        }

        val properties: MutableMap<String, String> = parseProperties(o)
        return OPDSAcquisition(relation, link, type, indirects, properties)
      } catch (e: Exception) {
        throw OPDSParseException(e)
      }
    }

    @Throws(JSONParseException::class)
    private fun findLink(o: ObjectNode): Link {
       /*
        * In current versions of the application, we separately store templated and non-templated
        * links.
        */

      if (o.has("linkBasic")) {
        return parseLinkExceptionally(
          URI.create("urn:embedded-link"),
          o.get("linkBasic")
        )
      }
      if (o.has("linkTemplated")) {
        return parseLinkExceptionally(
          URI.create("urn:embedded-link"),
          o.get("linkTemplated")
        )
      }
       /*
        * In older versions of the application, we only ever stored links that do not have templates.
        */
      return Link.LinkBasic(
        getURI(o, "uri"),
        null,
        null,
        null,
        null,
        null,
        null,
        null
      )
    }

    @Throws(JSONParseException::class)
    private fun parseProperties(o: ObjectNode): MutableMap<String, String> {
      val properties: MutableMap<String, String>
      if (o.has(PROPERTIES_FIELD)) {
        val obj = getObject(o, PROPERTIES_FIELD)
        val fieldIter = obj.fields()
        properties = HashMap()
        while (fieldIter.hasNext()) {
          val field = fieldIter.next()
          val fieldName = field.key
          val fieldValue = field.value
          properties.put(fieldName, checkString(fieldValue))
        }
      } else {
        properties = mutableMapOf()
      }
      return properties
    }

    @Throws(OPDSParseException::class)
    private fun parseIndirectAcquisition(jnode: JsonNode): OPDSIndirectAcquisition {
      try {
        val obj =
          checkObject(null, jnode)
        val type =
          parseRaisingException(getString(obj, "type"))
        val indirects =
          getArray(obj, INDIRECT_ACQUISITIONS_FIELD)
        val properties: MutableMap<String, String> =
          parseProperties(obj)

        return OPDSIndirectAcquisition(
          type,
          parseIndirectAcquisitions(indirects),
          properties
        )
      } catch (e: Exception) {
        throw OPDSParseException(e)
      }
    }

    @Throws(OPDSParseException::class)
    private fun parseIndirectAcquisitions(indirects: ArrayNode): MutableList<OPDSIndirectAcquisition> {
      val results: MutableList<OPDSIndirectAcquisition> =
        ArrayList(indirects.size())
      for (index in 0..<indirects.size()) {
        results.add(parseIndirectAcquisition(indirects.get(index)))
      }
      return results
    }

    @Throws(OPDSParseException::class)
    private fun parseAvailability(node: ObjectNode): OPDSAvailabilityType {
      try {
        if (node.has("loanable")) {
          return OPDSAvailabilityLoanable.get()
        }
        if (node.has("holdable")) {
          return OPDSAvailabilityHoldable.get()
        }

        if (node.has("loaned")) {
          val n = getObject(node, "loaned")
          val startDate: DateTime? =
            getTimestampOrNull(n, "start_date")
          val endDate: DateTime? =
            getTimestampOrNull(n, "end_date")
          val revoke: URI? =
            getURIOrNull(n, "revoke")
          return OPDSAvailabilityLoaned.get(startDate, endDate, revoke)
        }

        if (node.has("held")) {
          val n = getObject(node, "held")
          val startDate: DateTime? =
            getTimestampOrNull(n, "start_date")
          val position: Int? =
            getIntegerOrNull(n, "position")
          val endDate: DateTime? =
            getTimestampOrNull(n, "end_date")
          val revoke: URI? =
            getURIOrNull(n, "revoke")
          return OPDSAvailabilityHeld.get(
            startDate,
            position,
            endDate,
            revoke
          )
        }

        if (node.has("held_ready")) {
          val n = getObject(node, "held_ready")
          val endDate: DateTime? =
            getTimestampOrNull(n, "end_date")
          val revoke: URI? =
            getURIOrNull(n, "revoke")
          return OPDSAvailabilityHeldReady.get(endDate, revoke)
        }

        if (node.has("open_access")) {
          val n =
            getObject(
              node, "open_access"
            )
          val revoke: URI? =
            getURIOrNull(n, "revoke")
          return OPDSAvailabilityOpenAccess.get(revoke)
        }

        if (node.has("revoked")) {
          val n =
            getObject(
              node, "revoked"
            )
          val revoke = getURI(n, "revoke")
          return OPDSAvailabilityRevoked.get(revoke)
        }

        throw OPDSParseException("Expected availability information")
      } catch (e: JSONParseException) {
        throw OPDSParseException(e)
      }
    }

    @Throws(OPDSParseException::class)
    private fun parseCategory(jn: JsonNode): OPDSCategory {
      try {
        val o = checkObject(null, jn)
        val term = getString(o, "term")
        val scheme = getString(o, "scheme")
        val label: String? = getStringOrNull(o, "label")
        return OPDSCategory(term, scheme, label)
      } catch (e: JSONParseException) {
        throw OPDSParseException(e)
      }
    }

    @Throws(OPDSParseException::class)
    private fun parseLicensor(jn: JsonNode): DRMLicensor {
      try {
        val o = checkObject(null, jn)
        val vendor = getString(o, "vendor")
        val clientToken = getString(o, "clientToken")
        val deviceManager: String? = getStringOrNull(o, "deviceManager")
        return DRMLicensor(vendor, clientToken, deviceManager)
      } catch (e: JSONParseException) {
        throw OPDSParseException(e)
      }
    }
  }
}
