package org.nypl.simplified.ui.main

import com.io7m.jattribute.core.AttributeReadableType
import com.io7m.jattribute.core.AttributeSubscriptionType
import com.io7m.jattribute.core.AttributeType
import com.io7m.jattribute.core.Attributes
import org.nypl.simplified.threads.UIThread
import org.slf4j.LoggerFactory

/**
 * Convenience functions over [Attributes](com.io7m.jattribute.core.Attributes).
 */

object MainAttributes {

  private val logger =
    LoggerFactory.getLogger(MainAttributes::class.java)

  val attributes: Attributes =
    Attributes.create { ex -> this.logger.error("Uncaught exception in attribute: ", ex) }

  /**
   * Read events from the given readable attribute, and republish them to the target attribute
   * such that all updates will be observed on the Android UI thread.
   */

  fun <T> wrapAttribute(
    source: AttributeReadableType<T>,
    target: AttributeType<T>
  ): AttributeSubscriptionType {
    return source.subscribe { _, newValue -> UIThread.runOnUIThread { target.set(newValue) } }
  }
}
