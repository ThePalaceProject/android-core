package org.nypl.simplified.books.time.tracking

data class TimeTrackingServerResponseEntry(
  val id: String,
  val message: String,
  val status: Int
) {
  companion object {
    private const val STATUS_SUCCESS_MIN = 200
    private const val STATUS_SUCCESS_MAX = 299
    private const val STATUS_GONE = 410
    private const val STATUS_NOT_FOUND = 404
  }

  fun isStatusSuccess(): Boolean {
    return this.status in STATUS_SUCCESS_MIN..STATUS_SUCCESS_MAX
  }

  fun isStatusFailedPermanently(): Boolean {
    return this.status == STATUS_GONE || this.status == STATUS_NOT_FOUND
  }
}
