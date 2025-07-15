package org.nypl.simplified.ui.main

import org.nypl.drm.core.BoundlessServiceFactoryType
import org.nypl.drm.core.BoundlessServiceType
import org.slf4j.LoggerFactory
import java.io.File
import java.util.ServiceLoader

object MainBoundless {

  fun createBoundless(
    directory: File,
  ): BoundlessServiceType? {
    val logger = LoggerFactory.getLogger(MainBoundless::class.java)

    return try {
      val loader =
        ServiceLoader.load(BoundlessServiceFactoryType::class.java)
      val iterator =
        loader.iterator()

      while (iterator.hasNext()) {
        val factory = iterator.next()
        try {
          val service = factory.create(directory)
          logger.debug("Instantiated Boundless DRM service.")
          return service
        } catch (e: Throwable) {
          logger.error("Failed to instantiate Boundless DRM: ", e)
        }
      }

      logger.debug("No Boundless DRM support is available.")
      null
    } catch (e: Throwable) {
      logger.debug("No Boundless DRM support is available.")
      null
    }
  }
}
