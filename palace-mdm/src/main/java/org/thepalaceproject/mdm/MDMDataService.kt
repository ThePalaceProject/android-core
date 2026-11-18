package org.thepalaceproject.mdm

/**
 * A service that provides access to the most recent managed device (MDM)
 * configuration data.
 */
class MDMDataService : MDMDataServiceType {
  @Volatile
  private var mostRecent: MDMData? = null

  override fun mostRecent(): MDMData? = this.mostRecent

  override fun setMostRecent(data: MDMData) {
    this.mostRecent = data
  }
}
