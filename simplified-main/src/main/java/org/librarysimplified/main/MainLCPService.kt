package org.librarysimplified.main

import android.app.Application
import org.readium.r2.lcp.LcpService
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.downloads.android.AndroidDownloadManager
import org.readium.r2.shared.util.http.DefaultHttpClient

/**
 * The main LCP service.
 */

object MainLCPService {

  /**
   * Create an LCP service. The function (despite appearances), returns a `null` value if
   * the application is not compiled with `liblcp` present on the classpath.
   */

  fun createConditionally(
    context: Application
  ): LcpService? {
    val assetRetriever =
      AssetRetriever(
        contentResolver = context.contentResolver,
        httpClient = DefaultHttpClient()
      )

    return LcpService(
      context = context,
      assetRetriever = assetRetriever,
      downloadManager = AndroidDownloadManager(context)
    )
  }
}
