package org.thepalaceproject.opds.client

import com.io7m.jattribute.core.Attributes
import org.slf4j.LoggerFactory

internal object OPDSClientAttributes {

  private val attrLogger =
    LoggerFactory.getLogger(OPDSClient::class.java)

  val attributes =
    Attributes.create { e -> this.attrLogger.warn("Attribute error: ", e) }
}
