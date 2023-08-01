package org.nypl.simplified.books.time.tracking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import org.slf4j.LoggerFactory

class TimeTrackingConnectivityListener(
  private val context: Context,
  private val onConnectivityStateRetrieved: (Boolean) -> Unit
) {

  private val logger = LoggerFactory.getLogger(TimeTrackingConnectivityListener::class.java)

  private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      onConnectivityStateRetrieved(true)
      logger.debug("Connection available")
    }

    override fun onLost(network: Network) {
      onConnectivityStateRetrieved(false)
      logger.debug("Connection lost")
    }

    override fun onUnavailable() {
      onConnectivityStateRetrieved(false)
      logger.debug("Connection unavailable")
    }
  }

  init {
    val connectivityManager =
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      connectivityManager.registerDefaultNetworkCallback(networkCallback)
    } else {
      connectivityManager.registerNetworkCallback(
        NetworkRequest.Builder()
          .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
          .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
          .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
          .build(),
        networkCallback
      )
    }
  }
}
