package org.librarysimplified.viewer.preview

import android.content.Context
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.joda.time.Duration
import org.librarysimplified.services.api.Services
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.feeds.api.FeedEntry
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

class BookPreviewAudiobookFragment : Fragment(), AudioManager.OnAudioFocusChangeListener {

  private val logger =
    LoggerFactory.getLogger(BookPreviewAudiobookFragment::class.java)

  companion object {
    private const val BUNDLE_EXTRA_FILE =
      "org.librarysimplified.viewer.preview.BookPreviewAudiobookFragment.file"

    private const val BUNDLE_EXTRA_FEED_ENTRY =
      "org.librarysimplified.viewer.preview.BookPreviewAudiobookFragment.entry"

    private const val PLAYER_MOVE_THRESHOLD = 30000L

    fun newInstance(file: File, feedEntry: FeedEntry.FeedEntryOPDS): BookPreviewAudiobookFragment {
      return BookPreviewAudiobookFragment().apply {
        this.arguments = Bundle().apply {
          this.putSerializable(this@Companion.BUNDLE_EXTRA_FILE, file)
          this.putSerializable(this@Companion.BUNDLE_EXTRA_FEED_ENTRY, feedEntry)
        }
      }
    }
  }

  private val services = Services.serviceDirectory()

  private val covers =
    this.services.requireService(BookCoverProviderType::class.java)

  private val audioManager by lazy {
    this.requireActivity().getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }
  private var audioRequest: AudioFocusRequest? = null

  private val playerMediaReceiver by lazy {
    BookPreviewPlayerMediaReceiver(
      onAudioBecomingNoisy = {
        this.onPauseButtonPressed(
          abandonAudioFocus = false
        )
      }
    )
  }

  private val timeStrings by lazy {
    BookPreviewTimeUtils.SpokenTranslations.createFromResources(this.resources)
  }

  private val disposables = CompositeDisposable()

  private lateinit var audioFile: File
  private lateinit var feedEntry: FeedEntry.FeedEntryOPDS
  private lateinit var mediaPlayer: MediaPlayer
  private lateinit var previewAuthor: TextView
  private lateinit var previewBackwardButton: ImageView
  private lateinit var previewCover: ImageView
  private lateinit var previewCurrentTime: TextView
  private lateinit var previewForwardButton: ImageView
  private lateinit var previewPlayPauseButton: ImageView
  private lateinit var previewRemainingTime: TextView
  private lateinit var previewSeekbar: SeekBar
  private lateinit var previewTitle: TextView
  private lateinit var toolbar: Toolbar

  private var audioFocusDelayed = false
  private var clickedOnThumb = false
  private var playerPositionDragging = false
  private var playOnAudioFocus = false
  private var playWhenResuming = false

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_book_preview_audiobook, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.audioFile = this.requireArguments()
      .getSerializable(BUNDLE_EXTRA_FILE)
      as? File ?: throw RuntimeException("Invalid file")
    this.feedEntry = this.requireArguments()
      .getSerializable(BUNDLE_EXTRA_FEED_ENTRY)
      as? FeedEntry.FeedEntryOPDS ?: throw RuntimeException("Invalid entry")

    this.previewAuthor = view.findViewById(R.id.player_author)
    this.previewBackwardButton = view.findViewById(R.id.player_jump_backwards)
    this.previewCover = view.findViewById(R.id.player_cover)
    this.previewCurrentTime = view.findViewById(R.id.player_time)
    this.previewForwardButton = view.findViewById(R.id.player_jump_forwards)
    this.previewPlayPauseButton = view.findViewById(R.id.player_play_pause_button)
    this.previewRemainingTime = view.findViewById(R.id.player_remaining_time)
    this.previewSeekbar = view.findViewById(R.id.player_seekbar)
    this.previewTitle = view.findViewById(R.id.player_title)
    this.toolbar = view.findViewById(R.id.toolbar)

    this.requireActivity().registerReceiver(
      this.playerMediaReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    )

    this.configureToolbar()
    this.configurePlayer()
  }

  override fun onPause() {
    this.playWhenResuming = this.mediaPlayer.isPlaying
    if (this.mediaPlayer.isPlaying) {
      this.previewPlayPauseButton.performClick()
    }
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    if (this.playWhenResuming) {
      this.previewPlayPauseButton.performClick()
    }
  }

  override fun onDestroyView() {
    this.mediaPlayer.stop()
    this.mediaPlayer.release()
    this.disposables.clear()
    this.abandonAudioFocus()
    this.requireActivity().unregisterReceiver(this.playerMediaReceiver)
    super.onDestroyView()
  }

  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        if ((this.playOnAudioFocus || this.audioFocusDelayed) && !this.mediaPlayer.isPlaying) {
          this.audioFocusDelayed = false
          this.playOnAudioFocus = false
          this.startPlaying()
        }
      }

      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        if (this.mediaPlayer.isPlaying) {
          this.playOnAudioFocus = true
          this.audioFocusDelayed = false
          this.onPauseButtonPressed(
            abandonAudioFocus = false
          )
        }
      }

      AudioManager.AUDIOFOCUS_LOSS -> {
        this.audioFocusDelayed = false
        this.playOnAudioFocus = false
        this.onPauseButtonPressed(
          abandonAudioFocus = true
        )
      }
    }
  }

  private fun configureToolbar() {
    this.toolbar.setNavigationOnClickListener { this.requireActivity().finish() }
  }

  private fun configurePlayer() {
    this.covers.loadCoverInto(
      entry = this.feedEntry,
      hasBadge = false,
      imageView = this.previewCover,
      width = 0,
      height = 0
    )

    this.previewAuthor.text = this.feedEntry.feedEntry.authorsCommaSeparated
    this.previewTitle.text = this.feedEntry.feedEntry.title

    this.mediaPlayer = MediaPlayer()
    this.mediaPlayer.setDataSource(this.requireContext(), Uri.fromFile(this.audioFile))
    this.mediaPlayer.prepare()
    this.mediaPlayer.setOnPreparedListener {
      this.mediaPlayer.seekTo(0)
      this.previewSeekbar.max = this.mediaPlayer.duration
      this.previewSeekbar.setOnTouchListener { _, event -> this.handleTouchOnSeekbar(event) }
      this.updateRemainingTime(0)
      this.configurePlayerButtons()
      this.startTimer()
    }
    this.mediaPlayer.setOnCompletionListener {
      this.mediaPlayer.seekTo(0)
      this.onPauseButtonPressed(
        abandonAudioFocus = false
      )
    }
  }

  private fun startTimer() {
    this.disposables.add(
      Observable.interval(1000L, TimeUnit.MILLISECONDS)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {
          this.updatePlayerUI()
        }
    )
  }

  private fun updatePlayerUI() {
    if (!this.playerPositionDragging) {
      this.previewSeekbar.progress = this.mediaPlayer.currentPosition
    }

    val currentPosition = this.mediaPlayer.currentPosition.toLong()
    this.updateCurrentTime(currentPosition)
    this.updateRemainingTime(currentPosition)
  }

  private fun updateCurrentTime(currentPosition: Long) {
    this.previewCurrentTime.text =
      BookPreviewTimeUtils.hourMinuteSecondTextFromDuration(
        Duration.millis(currentPosition)
      )
    this.previewCurrentTime.contentDescription =
      this.playerTimeCurrentSpoken(currentPosition)
  }

  private fun updateRemainingTime(currentPosition: Long) {
    val duration = this.mediaPlayer.duration.toLong()

    this.previewRemainingTime.text =
      BookPreviewTimeUtils.hourMinuteSecondTextFromDuration(
        Duration.millis(duration - currentPosition)
      )
    this.previewRemainingTime.contentDescription =
      this.playerTimeRemainingSpoken(currentPosition, duration)
  }

  private fun onPauseButtonPressed(abandonAudioFocus: Boolean) {
    if (abandonAudioFocus) {
      this.abandonAudioFocus()
    }

    this.previewPlayPauseButton.setImageResource(R.drawable.play_icon)
    this.previewPlayPauseButton.contentDescription =
      this.getString(R.string.bookPreviewAccessibilityPlay)
    this.mediaPlayer.pause()
  }

  private fun onPlayButtonPressed() {
    when (this.requestAudioFocus()) {
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        this.logger.debug("Audio focus request granted")
        this.startPlaying()
      }

      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        this.logger.debug("Audio focus request delayed")
        this.audioFocusDelayed = true
      }

      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        // the system denied access to the audio focus, so we do nothing
        this.logger.debug("Audio focus request failed")
      }
    }
  }

  private fun abandonAudioFocus() {
    this.logger.debug("Abandoning audio focus")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (this.audioRequest != null) {
        this.audioManager.abandonAudioFocusRequest(this.audioRequest!!)
      }
    } else {
      this.audioManager.abandonAudioFocus(this)
    }
  }

  private fun requestAudioFocus(): Int {
    // initiate the audio playback attributes
    val playbackAttributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      this.audioRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(playbackAttributes)
        .setWillPauseWhenDucked(true)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(this)
        .build()

      this.audioManager.requestAudioFocus(this.audioRequest!!)
    } else {
      this.audioManager.requestAudioFocus(
        this, AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN
      )
    }
  }

  private fun startPlaying() {
    this.previewPlayPauseButton.setImageResource(R.drawable.pause_icon)
    this.previewPlayPauseButton.contentDescription =
      this.getString(R.string.bookPreviewAccessibilityPause)
    this.mediaPlayer.start()
  }

  private fun configurePlayerButtons() {
    this.previewPlayPauseButton.setOnClickListener {
      if (this.mediaPlayer.isPlaying) {
        this.onPauseButtonPressed(
          abandonAudioFocus = true
        )
      } else {
        this.onPlayButtonPressed()
      }
    }

    this.previewForwardButton.setOnClickListener {
      val duration = this.mediaPlayer.duration
      val position = this.mediaPlayer.currentPosition + PLAYER_MOVE_THRESHOLD
      this.mediaPlayer.seekTo(
        if (position <= duration) {
          position.toInt()
        } else {
          duration
        }
      )
    }

    this.previewBackwardButton.setOnClickListener {
      val position = this.mediaPlayer.currentPosition - PLAYER_MOVE_THRESHOLD
      this.mediaPlayer.seekTo(
        if (position >= 0) {
          position.toInt()
        } else {
          0
        }
      )
    }
  }

  private fun handleTouchOnSeekbar(event: MotionEvent?): Boolean {
    when (event?.action) {
      MotionEvent.ACTION_DOWN -> {
        this.clickedOnThumb = this.wasSeekbarThumbClicked(event)
        if (!this.clickedOnThumb) {
          return true
        }
      }

      MotionEvent.ACTION_UP -> {
        if (this.clickedOnThumb && this.playerPositionDragging) {
          this.playerPositionDragging = false
          this.clickedOnThumb = false
          this.updateUIOnProgressBarDragging()
        } else {
          this.playerPositionDragging = false
          this.clickedOnThumb = false
          return true
        }
      }

      MotionEvent.ACTION_MOVE -> {
        if (!this.clickedOnThumb) {
          return true
        }
        this.playerPositionDragging = true
        this.updateUIOnProgressBarDragging()
      }

      MotionEvent.ACTION_CANCEL -> {
        this.playerPositionDragging = false
        this.clickedOnThumb = false
      }
    }

    return this.previewSeekbar.onTouchEvent(event)
  }

  private fun wasSeekbarThumbClicked(event: MotionEvent): Boolean {
    return this.previewSeekbar.thumb.bounds.contains(event.x.toInt(), event.y.toInt())
  }

  private fun updateUIOnProgressBarDragging() {
    this.logger.debug("updateUIOnProgressBarDragging")

    this.mediaPlayer.seekTo(this.previewSeekbar.progress)
  }

  private fun playerTimeCurrentSpoken(offsetMilliseconds: Long): String {
    return this.getString(
      R.string.bookPreviewAccessibilityTimeCurrent,
      BookPreviewTimeUtils.hourMinuteSecondSpokenFromDuration(
        this.timeStrings,
        Duration.millis(offsetMilliseconds)
      )
    )
  }

  private fun playerTimeRemainingSpoken(
    offsetMilliseconds: Long,
    duration: Long
  ): String {
    val remaining = Duration.millis(duration - offsetMilliseconds)

    return this.getString(
      R.string.bookPreviewAccessibilityTimeRemaining,
      BookPreviewTimeUtils.hourMinuteSecondSpokenFromDuration(this.timeStrings, remaining)
    )
  }
}
