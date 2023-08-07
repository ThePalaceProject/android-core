package org.nypl.simplified.main

import android.content.Context
import com.transifex.txnative.LocaleState
import com.transifex.txnative.TxNative
import com.transifex.txnative.missingpolicy.WrappedStringPolicy
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import java.util.Properties

/**
 * Functions to enable Transifex string translation.
 */

object MainTransifex {

  private val logger = LoggerFactory.getLogger(MainTransifex::class.java)

  private fun loadTransifexToken(context: Context): String? {
    return try {
      context.assets.open("secrets.conf").use { stream ->
        val props = Properties()
        props.load(stream)
        props.getProperty("transifex.token")
      }
    } catch (e: FileNotFoundException) {
      this.logger.warn("Failed to initialize Transifex; secrets.conf not found")
      null
    } catch (e: Exception) {
      this.logger.warn("Failed to initialize Transifex", e)
      null
    }
  }

  /**
   * Configure Transifex. Does nothing if the Transifex token is not present.
   */

  fun configure(applicationContext: Context) {
    val token = loadTransifexToken(applicationContext)
    if (token == null) {
      this.logger.warn("Failed to initialize Transifex; transifex.token key not present")
      return
    }

    val localeState =
      LocaleState(
        applicationContext,
        "en",
        arrayOf("en", "es"),
        null
      )

    val stringPolicy =
      if (BuildConfig.DEBUG) {
        WrappedStringPolicy("[[", "]]")
      } else {
        WrappedStringPolicy(null, null)
      }

    this.logger.debug("Initializing Transifex")
    TxNative.init(
      applicationContext,
      localeState,
      token,
      null,
      null,
      stringPolicy
    )

    this.logger.debug("Retrieving Transifex string translations")
    TxNative.fetchTranslations(null, null)
  }
}
