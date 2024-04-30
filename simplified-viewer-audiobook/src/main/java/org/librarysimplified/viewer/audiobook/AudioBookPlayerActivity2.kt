package org.librarysimplified.viewer.audiobook

import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.TxContextWrappingDelegate2
import androidx.fragment.app.Fragment
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.audiobook.views.PlayerModelState
import org.librarysimplified.audiobook.views.PlayerViewCommand

class AudioBookPlayerActivity2 : AppCompatActivity(R.layout.audio_book_player_base) {

  private val playerExtensions: List<PlayerExtensionType> = listOf()
  private var fragmentNow: Fragment = AudioBookLoadingFragment2()
  private var subscriptions: CompositeDisposable = CompositeDisposable()

  private val appCompatDelegate: TxContextWrappingDelegate2 by lazy {
    TxContextWrappingDelegate2(super.getDelegate())
  }

  override fun getDelegate(): AppCompatDelegate {
    return this.appCompatDelegate
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.stateEvents.subscribe(this::onModelStateEvent))
    this.subscriptions.add(PlayerModel.viewCommands.subscribe(this::onPlayerViewCommand))
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.dispose()
  }

  @UiThread
  private fun onPlayerEvent(
    event: PlayerEvent
  ) {
    PlayerUIThread.checkIsUIThread()
  }

  @UiThread
  private fun onModelStateEvent(
    state: PlayerModelState
  ) {
    PlayerUIThread.checkIsUIThread()
  }

  @UiThread
  private fun onPlayerViewCommand(
    command: PlayerViewCommand
  ) {
    PlayerUIThread.checkIsUIThread()
  }

  private fun switchFragment(
    fragment: Fragment
  ) {
    this.fragmentNow = fragment
    this.supportFragmentManager.beginTransaction()
      .replace(R.id.audio_book_player_fragment_holder, fragment)
      .commit()
  }
}
