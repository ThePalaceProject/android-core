package org.nypl.simplified.main

import android.content.Context
import org.readium.r2.lcp.LcpService

/**
 * The main LCP service.
 */

object MainLCPService {

  /**
   * Create an LCP service. The function (despite appearances), returns a `null` value if
   * the application is not compiled with `liblcp` present on the classpath.
   */

  fun createConditionally(
    context: Context
  ): LcpService? {
    return LcpService(context)
  }
}
