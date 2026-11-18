package org.thepalaceproject.mdm

/**
 * A service that provides access to the most recent managed device (MDM)
 * configuration data.
 *
 * The data is updated whenever the configuration is read, whether at
 * application startup or in response to a configuration change.
 */
interface MDMDataServiceType {
  /**
   * @return The most recent configuration data, or `null` if the
   * configuration has not yet been read
   */
  fun mostRecent(): MDMData?

  /**
   * Record the most recent configuration data.
   *
   * @param data The configuration data
   */
  fun setMostRecent(data: MDMData)
}
