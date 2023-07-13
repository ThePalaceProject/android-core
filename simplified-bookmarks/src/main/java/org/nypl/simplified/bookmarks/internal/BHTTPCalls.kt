package org.nypl.simplified.bookmarks.internal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType.Method.Delete
import org.librarysimplified.http.api.LSHTTPRequestBuilderType.Method.Post
import org.librarysimplified.http.api.LSHTTPRequestBuilderType.Method.Put
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.bookmarks.api.BookmarkAnnotation
import org.nypl.simplified.bookmarks.api.BookmarkAnnotationsJSON
import org.nypl.simplified.bookmarks.api.BookmarkHTTPCallsType
import org.nypl.simplified.json.core.JSONParserUtilities
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * A minimal class around the various annotations and user profile REST calls.
 */

class BHTTPCalls(
  private val objectMapper: ObjectMapper,
  private val http: LSHTTPClientType
) : BookmarkHTTPCallsType {

  private val logger = LoggerFactory.getLogger(BHTTPCalls::class.java)

  override fun bookmarksGet(
    annotationsURI: URI,
    credentials: AccountAuthenticationCredentials
  ): List<BookmarkAnnotation> {
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val request =
      this.http.newRequest(annotationsURI)
        .setAuthorization(auth)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK ->
          this.deserializeBookmarksFromStream(status.bodyStream ?: this.emptyStream())
        is LSHTTPResponseStatus.Responded.Error ->
          this.logAndFail(annotationsURI, status)
        is LSHTTPResponseStatus.Failed ->
          throw status.exception
      }
    }
  }

  override fun bookmarkDelete(
    bookmarkURI: URI,
    credentials: AccountAuthenticationCredentials
  ): Boolean {
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val request =
      this.http.newRequest(bookmarkURI)
        .setAuthorization(auth)
        .setMethod(Delete)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          true
        }
        is LSHTTPResponseStatus.Responded.Error -> {
          this.logAndFail(bookmarkURI, status)
        }
        is LSHTTPResponseStatus.Failed -> {
          throw status.exception
        }
      }
    }
  }

  private fun emptyStream() = ByteArrayInputStream(ByteArray(0))

  override fun bookmarkAdd(
    annotationsURI: URI,
    credentials: AccountAuthenticationCredentials,
    bookmark: BookmarkAnnotation
  ): URI? {
    val data =
      BookmarkAnnotationsJSON.serializeBookmarkAnnotationToBytes(this.objectMapper, bookmark)
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val post =
      Post(data, MIMEType("application", "ld+json", mapOf()))
    val request =
      this.http.newRequest(annotationsURI)
        .setAuthorization(auth)
        .setMethod(post)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          val receivedBookmark = objectMapper.readTree(status.bodyStream ?: this.emptyStream())
          try {
            URI.create(receivedBookmark.get("id").asText())
          } catch (exception: Exception) {
            this.logger.error(exception.message)
            null
          }
        }
        is LSHTTPResponseStatus.Responded.Error -> {
          this.logAndFail(annotationsURI, status)
        }
        is LSHTTPResponseStatus.Failed -> {
          throw status.exception
        }
      }
    }
  }

  override fun syncingEnable(
    settingsURI: URI,
    credentials: AccountAuthenticationCredentials,
    enabled: Boolean
  ) {
    val data =
      this.serializeSynchronizeEnableData(enabled)
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val put =
      Put(data, MIMEType("vnd.librarysimplified", "user-profile+json", mapOf()))
    val request =
      this.http.newRequest(settingsURI)
        .setAuthorization(auth)
        .setMethod(put)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK ->
          Unit
        is LSHTTPResponseStatus.Responded.Error ->
          this.logAndFail(settingsURI, status)
        is LSHTTPResponseStatus.Failed ->
          throw status.exception
      }
    }
  }

  override fun syncingIsEnabled(
    settingsURI: URI,
    credentials: AccountAuthenticationCredentials
  ): Boolean {
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val request =
      this.http.newRequest(settingsURI)
        .setAuthorization(auth)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK ->
          this.deserializeSyncingEnabledFromStream(status.bodyStream ?: emptyStream())
        is LSHTTPResponseStatus.Responded.Error ->
          this.logAndFail(settingsURI, status)
        is LSHTTPResponseStatus.Failed ->
          throw status.exception
      }
    }
  }

  private fun <T> logAndFail(
    uri: URI,
    error: LSHTTPResponseStatus.Responded.Error
  ): T {
    val problemReport = error.properties.problemReport
    if (problemReport != null) {
      this.logger.error("detail: {}", problemReport.detail)
      this.logger.error("status: {}", problemReport.status)
      this.logger.error("title:  {}", problemReport.title)
      this.logger.error("type:   {}", problemReport.type)
    }
    throw IOException("$uri received ${error.properties.status} ${error.properties.message}")
  }

  private fun deserializeBookmarksFromStream(value: InputStream): List<BookmarkAnnotation> {
    val node =
      this.objectMapper.readTree(value)
    val response =
      BookmarkAnnotationsJSON.deserializeBookmarkAnnotationResponseFromJSON(
        objectMapper = this.objectMapper,
        node = node
      )
    return response.first.items
  }

  private fun deserializeSyncingEnabledFromStream(value: InputStream): Boolean {
    return this.deserializeSyncingEnabledFromJSON(this.objectMapper.readTree(value))
  }

  private fun deserializeSyncingEnabledFromJSON(node: JsonNode): Boolean {
    return this.deserializeSyncingEnabledFromJSONObject(JSONParserUtilities.checkObject(null, node))
  }

  private fun deserializeSyncingEnabledFromJSONObject(node: ObjectNode): Boolean {
    val settings = JSONParserUtilities.getObjectOrNull(node, "settings")
    return if (settings != null) {
      if (settings.has("simplified:synchronize_annotations")) {
        val text: String? = settings.get("simplified:synchronize_annotations").asText()
        text == "true"
      } else {
        true
      }
    } else {
      true
    }
  }

  private fun serializeSynchronizeEnableData(enabled: Boolean): ByteArray {
    val settingsNode = this.objectMapper.createObjectNode()
    settingsNode.put("simplified:synchronize_annotations", enabled)
    val node = this.objectMapper.createObjectNode()
    node.put("settings", settingsNode)
    return this.objectMapper.writeValueAsBytes(node)
  }
}
