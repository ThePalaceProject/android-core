package org.nypl.simplified.books.time.tracking

class TimeTrackingResponseEntry(
  val id: String,
  val message: String,
  val status: Int
) {
  companion object {
    private const val STATUS_SUCCESS_MIN = 200
    private const val STATUS_SUCCESS_MAX = 299
    private const val STATUS_GONE = 410
  }

  fun isStatusSuccess(): Boolean {
    return status in STATUS_SUCCESS_MIN..STATUS_SUCCESS_MAX
  }

  fun isStatusGone(): Boolean {
    return status == STATUS_GONE
  }
}
