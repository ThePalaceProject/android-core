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
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.api.AccountAuthenticationCredentials
import org.nypl.simplified.accounts.database.api.AccountType
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
    account: AccountType,
    annotationsURI: URI,
    credentials: AccountAuthenticationCredentials
  ): List<BookmarkAnnotation> {
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val request =
      this.http.newRequest(annotationsURI)
        .setAuthorization(auth)
        .addCredentialsToProperties(credentials)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          account.updateBasicTokenCredentials(status.getAccessToken())
          this.deserializeBookmarksFromStream(status.bodyStream ?: this.emptyStream())
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

  override fun bookmarkDelete(
    account: AccountType,
    bookmarkURI: URI,
    credentials: AccountAuthenticationCredentials
  ): Boolean {
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val request =
      this.http.newRequest(bookmarkURI)
        .setAuthorization(auth)
        .addCredentialsToProperties(credentials)
        .setMethod(Delete)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          account.updateBasicTokenCredentials(status.getAccessToken())
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
    account: AccountType,
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
        .addCredentialsToProperties(credentials)
        .setMethod(post)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          account.updateBasicTokenCredentials(status.getAccessToken())

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
    account: AccountType,
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
        .addCredentialsToProperties(credentials)
        .setMethod(put)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          account.updateBasicTokenCredentials(status.getAccessToken())
        }
        is LSHTTPResponseStatus.Responded.Error -> {
          this.logAndFail(settingsURI, status)
        }
        is LSHTTPResponseStatus.Failed ->
          throw status.exception
      }
    }
  }

  override fun syncingIsEnabled(
    account: AccountType,
    settingsURI: URI,
    credentials: AccountAuthenticationCredentials
  ): Boolean {
    val auth =
      AccountAuthenticatedHTTP.createAuthorization(credentials)
    val request =
      this.http.newRequest(settingsURI)
        .setAuthorization(auth)
        .addCredentialsToProperties(credentials)
        .build()

    return request.execute().use { response ->
      when (val status = response.status) {
        is LSHTTPResponseStatus.Responded.OK -> {
          account.updateBasicTokenCredentials(status.getAccessToken())
          this.deserializeSyncingEnabledFromStream(status.bodyStream ?: emptyStream())
        }
        is LSHTTPResponseStatus.Responded.Error -> {
          this.logAndFail(settingsURI, status)
        }
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
