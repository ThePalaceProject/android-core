package org.nypl.simplified.viewer.preview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

class BookPreviewPlayerMediaReceiver(
  private val onAudioBecomingNoisy: () -> Unit
) : BroadcastReceiver() {

  override fun onReceive(context: Context?, intent: Intent?) {
    if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
      onAudioBecomingNoisy()
    }
  }
}
