package org.nypl.simplified.main

import android.content.Context
import android.content.pm.PackageManager
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients
import java.util.concurrent.TimeUnit

object MainHTTP {

  fun create(
    context: Context
  ): LSHTTPClientType {
    val (name, version) =
      try {
        val pkgManager = context.packageManager
        val pkgInfo = pkgManager.getPackageInfo(context.packageName, 0)
        Pair(pkgInfo.packageName, pkgInfo.versionName)
      } catch (e: PackageManager.NameNotFoundException) {
        Pair("Unavailable", "0.0.0")
      }

    val configuration =
      LSHTTPClientConfiguration(
        applicationName = name,
        applicationVersion = version,
        // TODO: The 5 minute timeout is for download of large LCP audiobooks (in BorrowLCP).
        // Otherwise, the default of 1 minute would be sufficient. In the future we might want to
        // allow per-request timeouts.
        timeout = Pair(5L, TimeUnit.MINUTES)
      )

    return LSHTTPClients().create(context, configuration)
  }
}
