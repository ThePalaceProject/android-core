package org.nypl.simplified.viewer.preview

import android.content.res.Resources
import org.joda.time.Duration
import org.joda.time.Period
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder

object BookPreviewTimeUtils {

  data class SpokenTranslations(

    /**
     * The word for "hours" in the current language.
     */

    val hoursText: String,

    /**
     * The word for "hour" in the current language.
     */

    val hourText: String,

    /**
     * The word for "minutes" in the current language.
     */

    val minutesText: String,

    /**
     * The word for "minute" in the current language.
     */

    val minuteText: String,

    /**
     * The word for "seconds" in the current language.
     */

    val secondsText: String,

    /**
     * The word for "second" in the current language.
     */

    val secondText: String
  ) {

    fun minutes(minutes: Long): String {
      return if (minutes > 1) {
        this.minutesText
      } else {
        this.minuteText
      }
    }

    fun hours(hours: Long): String {
      return if (hours > 1) {
        this.hoursText
      } else {
        this.hourText
      }
    }

    fun seconds(seconds: Long): String {
      return if (seconds > 1) {
        this.secondsText
      } else {
        this.secondText
      }
    }

    companion object {

      fun createFromResources(resources: Resources): SpokenTranslations {
        return SpokenTranslations(
          hoursText = resources.getString(R.string.bookPreviewAccessibilityHours),
          hourText = resources.getString(R.string.bookPreviewAccessibilityHour),
          minutesText = resources.getString(R.string.bookPreviewAccessibilityMinutes),
          minuteText = resources.getString(R.string.bookPreviewAccessibilityMinute),
          secondsText = resources.getString(R.string.bookPreviewAccessibilitySeconds),
          secondText = resources.getString(R.string.bookPreviewAccessibilitySecond)
        )
      }
    }
  }

  private val hourMinuteSecondFormatter: PeriodFormatter =
    PeriodFormatterBuilder()
      .printZeroAlways()
      .minimumPrintedDigits(2)
      .appendHours()
      .appendLiteral(":")
      .appendMinutes()
      .appendLiteral(":")
      .appendSeconds()
      .toFormatter()

  fun hourMinuteSecondTextFromDuration(duration: Duration?): String {
    return if (duration != null) {
      hourMinuteSecondFormatter.print(duration.toPeriod())
    } else {
      ""
    }
  }

  fun hourMinuteSecondSpokenFromDuration(
    translations: SpokenTranslations,
    duration: Duration
  ): String {
    val builder = StringBuilder(64)
    var period = duration.toPeriod()

    val hours = period.toStandardHours()
    if (hours.hours > 0) {
      builder.append(hours.hours)
      builder.append(' ')
      builder.append(translations.hours(hours.hours.toLong()))
      builder.append(' ')
      period = period.minus(hours)
    }

    val minutes = period.toStandardMinutes()
    if (minutes.minutes > 0) {
      builder.append(minutes.minutes)
      builder.append(' ')
      builder.append(translations.minutes(minutes.minutes.toLong()))
      builder.append(' ')
      period = period.minus(Period.minutes(minutes.minutes))
    }

    val seconds = period.toStandardSeconds()
    if (seconds.seconds > 0) {
      builder.append(seconds.seconds)
      builder.append(' ')
      builder.append(translations.seconds(seconds.seconds.toLong()))
    }

    return builder.toString().trim()
  }
}
