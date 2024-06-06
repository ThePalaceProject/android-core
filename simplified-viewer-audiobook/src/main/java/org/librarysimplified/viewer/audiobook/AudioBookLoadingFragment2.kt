package org.librarysimplified.viewer.audiobook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.views.PlayerModel

class AudioBookLoadingFragment2 : Fragment(R.layout.audio_book_player_loading) {

  private lateinit var progressText: TextView
  private lateinit var progressBar: ProgressBar
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val layout =
      inflater.inflate(R.layout.audio_book_player_loading, container, false)

    this.progressText =
      layout.findViewById(R.id.progressText)
    this.progressBar =
      layout.findViewById(R.id.progressBar)

    this.progressBar.isIndeterminate = true
    this.progressText.visibility = View.INVISIBLE
    return layout
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions.add(PlayerModel.manifestDownloadEvents.subscribe(this::onManifestEvent))
    this.subscriptions.add(PlayerModel.singleLicenseCheckEvents.subscribe(this::onLicenseEvent))
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.dispose()
  }

  private fun onLicenseEvent(
    event: SingleLicenseCheckStatus
  ) {
    this.progressText.text = event.message
  }

  private fun onManifestEvent(
    event: ManifestFulfillmentEvent
  ) {
    this.progressText.text = event.message
  }
}
