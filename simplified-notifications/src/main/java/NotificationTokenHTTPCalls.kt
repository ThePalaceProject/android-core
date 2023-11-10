package org.nypl.simplified.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.firebase.messaging.FirebaseMessaging
import one.irradia.mime.api.MIMEType
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPRequestBuilderType
import org.librarysimplified.http.api.LSHTTPResponseStatus
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.addCredentialsToProperties
import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP.getAccessToken
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.concurrent.ExecutorService

class NotificationTokenHTTPCalls(
  private val http: LSHTTPClientType,
  private val executor: ExecutorService
) : NotificationTokenHTTPCallsType {

  private val logger =
    LoggerFactory.getLogger(NotificationTokenHTTPCalls::class.java)

  private val firebaseInstance by lazy { FirebaseMessaging.getInstance() }

  override fun registerFCMTokenForProfileAccounts(profile: ProfileReadableType) {
    profile.accounts().values.forEach { account ->
      registerFCMTokenForProfileAccount(
        account = account
      )
    }
  }

  override fun registerFCMTokenForProfileAccount(
    account: AccountType
  ) {
    val credentials = account.loginState.credentials
    val originalUrl = credentials?.deviceRegistrationURI

    if (originalUrl == null) {
      logger.debug("Account {} doesn't have a device registration uri", account.id)
      return
    }

    firebaseInstance.token
      .addOnSuccessListener { token ->
        logger.debug("Success fetching FCM Token: {}", token)

        val queryUrl = buildString {
          this.append(originalUrl)
          this.append("?")
          this.append("device_token=")
          this.append(URLEncoder.encode(token, "utf-8"))
        }

        val request = this.http.newRequest(queryUrl)
          .setAuthorization(AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials))
          .addCredentialsToProperties(credentials)
          .build()

        this.executor.execute {
          val response = request.execute()
          when (val status = response.status) {
            is LSHTTPResponseStatus.Responded.OK -> {
              account.updateBasicTokenCredentials(status.getAccessToken())
              logger.debug("The account {} has the FCM token {}", account.id, token)
            }

            is LSHTTPResponseStatus.Responded.Error -> {
              if (status.properties.status == HttpURLConnection.HTTP_NOT_FOUND) {
                logger.debug(
                  "The account {} doesn't have the FCM token {}. Let's send it...",
                  account.id,
                  token
                )
                setFCMTokenForAccount(
                  account = account,
                  token = token,
                  url = originalUrl.toString()
                )
              } else {
                logger.error(
                  "Failed to retrieve FCM Token for account {}: {}",
                  account.id,
                  status.properties.message
                )
              }
            }

            is LSHTTPResponseStatus.Failed ->
              logger.error(
                "Failed to retrieve FCM Token for account {}",
                account.id,
                status.exception
              )
          }
        }
      }
      .addOnFailureListener { exception ->
        logger.error("Failed to fetch Firebase token: ", exception)
      }
  }

  override fun deleteFCMTokenForProfileAccount(
    account: AccountType
  ) {
    val credentials = account.loginState.credentials
    val url = credentials?.deviceRegistrationURI

    if (url == null) {
      logger.debug("Account {} doesn't have a device registration uri", account.id)
      return
    }

    firebaseInstance.token
      .addOnSuccessListener { token ->
        logger.debug("Success fetching FCM Token: {}", token)

        val request = this.http.newRequest(url)
          .setAuthorization(AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials))
          .addCredentialsToProperties(credentials)
          .setMethod(
            LSHTTPRequestBuilderType.Method.Delete(
              serializeNotificationToken(
                token = token
              ),
              MIMEType("application", "json", mapOf())
            )
          )
          .build()

        this.executor.execute {
          val response = request.execute()
          when (val status = response.status) {
            is LSHTTPResponseStatus.Responded.OK -> {
              account.updateBasicTokenCredentials(status.getAccessToken())
              logger.debug("Deleted FCM token for account {}", account.id)
            }

            is LSHTTPResponseStatus.Responded.Error -> {
              logger.error(
                "Failed to delete FCM Token for account {}: {}",
                account.id,
                status.properties.message
              )
            }

            is LSHTTPResponseStatus.Failed ->
              logger.error(
                "Failed to delete FCM Token for account {}",
                account.id,
                status.exception
              )
          }
        }
      }
      .addOnFailureListener { exception ->
        logger.error("Failed to fetch Firebase token: ", exception)
      }
  }

  private fun setFCMTokenForAccount(account: AccountType, token: String, url: String) {
    val credentials = account.loginState.credentials

    val request = http.newRequest(url)
      .setAuthorization(AccountAuthenticatedHTTP.createAuthorizationIfPresent(credentials))
      .addCredentialsToProperties(credentials)
      .setMethod(
        LSHTTPRequestBuilderType.Method.Put(
          serializeNotificationToken(
            token = token
          ),
          MIMEType("application", "json", mapOf())
        )
      )
      .build()

    val response = request.execute()
    when (val status = response.status) {
      is LSHTTPResponseStatus.Responded.OK -> {
        logger.debug("FCM Token successfully set for account {}", account.id)
        account.updateBasicTokenCredentials(status.getAccessToken())
      }

      is LSHTTPResponseStatus.Responded.Error -> {
        logger.error(
          "Failed to set FCM Token for account {}: {}",
          account.id,
          status.properties.message
        )
      }

      is LSHTTPResponseStatus.Failed ->
        logger.error("Failed to set FCM Token for account {}", account.id, status.exception)
    }
  }

  private fun serializeNotificationToken(token: String): ByteArray {
    val objectMapper = ObjectMapper()
    val node = objectMapper.createObjectNode()
    node.put("device_token", token)
    node.put("token_type", "FCMAndroid")
    return objectMapper.writeValueAsBytes(node)
  }
}
