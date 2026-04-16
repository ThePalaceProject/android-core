package org.nypl.simplified.ui.auto

import org.nypl.simplified.threads.NamedThreadPools

/**
 * A background thread used by Android Auto media operations.
 */

object PalaceAutoThread {

  val executor =
    NamedThreadPools.namedThreadPool(1, "palace-auto", 0)
}
