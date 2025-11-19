package org.nypl.simplified.ui.main

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object MainNotifications {

  fun requestPermissions(
    activity: Activity
  ) {
    activity.requestPermissions(
      arrayOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"
      ),
      1000
    )
  }

  fun notificationsArePermitted(
    activity: Activity
  ): Boolean {
    val notificationsOk =
      ContextCompat.checkSelfPermission(
        activity,
        "android.permission.POST_NOTIFICATIONS"
      ) == PackageManager.PERMISSION_GRANTED

    val foregroundOk =
      ContextCompat.checkSelfPermission(
        activity,
        "android.permission.FOREGROUND_SERVICE",
      ) == PackageManager.PERMISSION_GRANTED

    val foregroundMediaOk =
      ContextCompat.checkSelfPermission(
        activity,
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
      ) == PackageManager.PERMISSION_GRANTED

    return notificationsOk && foregroundOk && foregroundMediaOk
  }

  fun requestDropPermissions(
    activity: Activity
  ) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, MainApplication.application.packageName)
    activity.startActivity(intent)
  }
}
