package org.nypl.simplified.ui.main

import android.content.Context
import org.nypl.simplified.books.audio.AudioBookOverdriveSecretServiceType
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Properties

/**
 * A service for providing Overdrive secrets.
 */

class MainOverdriveSecretService private constructor(
  override val clientKey: String?,
  override val clientPass: String?
) : AudioBookOverdriveSecretServiceType {

  companion object {

    private val logger =
      LoggerFactory.getLogger(MainOverdriveSecretService::class.java)

    fun createConditionally(
      context: Context
    ): AudioBookOverdriveSecretServiceType? {
      return try {
        context.assets.open("secrets.conf").use(Companion::create)
      } catch (e: FileNotFoundException) {
        logger.warn("failed to initialize Overdrive; secrets.conf not found")
        null
      } catch (e: Exception) {
        logger.warn("failed to initialize Overdrive", e)
        null
      }
    }

    fun create(
      stream: InputStream
    ): AudioBookOverdriveSecretServiceType {
      val properties =
        Properties().apply { load(stream) }

      val clientKey =
        properties.getProperty("overdrive.prod.client.key")
      val clientPass =
        properties.getProperty("overdrive.prod.client.secret")

      return MainOverdriveSecretService(
        clientKey = clientKey,
        clientPass = clientPass
      )
    }
  }
}
