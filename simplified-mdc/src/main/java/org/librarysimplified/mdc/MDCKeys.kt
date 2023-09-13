package org.librarysimplified.mdc

import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.Some
import org.slf4j.MDC

/**
 * Conventional key names for MDC.
 *
 * @see "https://logback.qos.ch/manual/mdc.html"
 */

object MDCKeys {

  /**
   * The external ID of the current account provider as it appeared in the library registry.
   */

  const val ACCOUNT_PROVIDER_ID =
    "AccountProviderID"

  /**
   * The name of the current account provider as it appeared in the library registry.
   */

  const val ACCOUNT_PROVIDER_NAME =
    "AccountProviderName"

  /**
   * The hashed internal application ID of the current book.
   */

  const val BOOK_INTERNAL_ID =
    "BookInternalID"

  /**
   * The internal application ID of the current account.
   */

  const val ACCOUNT_INTERNAL_ID =
    "AccountInternalID"

  /**
   * The title of the current book.
   */

  const val BOOK_TITLE =
    "BookTitle"

  /**
   * The publisher of the current book.
   */

  const val BOOK_PUBLISHER =
    "BookPublisher"

  /**
   * Convenience function to set optional values.
   */

  fun put(key: String, value: OptionType<String>) {
    if (value is Some<String>) {
      MDC.put(key, value.get())
    }
  }
}
