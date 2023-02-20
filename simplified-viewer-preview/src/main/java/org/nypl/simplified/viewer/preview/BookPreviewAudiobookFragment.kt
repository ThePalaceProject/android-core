package org.nypl.simplified.viewer.preview

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

  companion object {
    private const val BUNDLE_EXTRA_FILE =
      "org.nypl.simplified.viewer.preview.BookPreviewAudiobookFragment.file"

    private const val BUNDLE_EXTRA_FEED_ENTRY =
      "org.nypl.simplified.viewer.preview.BookPreviewAudiobookFragment.entry"

    private const val PLAYER_MOVE_THRESHOLD = 30000L

    fun newInstance(file: File, feedEntry: FeedEntry.FeedEntryOPDS): BookPreviewAudiobookFragment {
      return BookPreviewAudiobookFragment().apply {
        arguments = Bundle().apply {
          putSerializable(BUNDLE_EXTRA_FILE, file)
          putSerializable(BUNDLE_EXTRA_FEED_ENTRY, feedEntry)
        }
      }
    }
  }

  private val logger = LoggerFactory.getLogger(BookPreviewAudiobookFragment::class.java)

  private val services = Services.serviceDirectory()

  private val covers =
    services.requireService(BookCoverProviderType::class.java)

  private val audioManager by lazy {
    requireActivity().getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }
  private var audioRequest: AudioFocusRequest? = null

  private val playerMediaReceiver by lazy {
    BookPreviewPlayerMediaReceiver(
      onAudioBecomingNoisy = {
        onPauseButtonPressed(
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

    this.audioFile = requireArguments().getSerializable(BUNDLE_EXTRA_FILE)
      as? File ?: throw RuntimeException("Invalid file")
    this.feedEntry = requireArguments().getSerializable(BUNDLE_EXTRA_FEED_ENTRY)
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

    requireActivity().registerReceiver(
      playerMediaReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    )

    configureToolbar()
    configurePlayer()
  }

  override fun onPause() {
    playWhenResuming = mediaPlayer.isPlaying
    if (mediaPlayer.isPlaying) {
      previewPlayPauseButton.performClick()
    }
    super.onPause()
  }

  override fun onResume() {
    super.onResume()
    if (playWhenResuming) {
      previewPlayPauseButton.performClick()
    }
  }

  override fun onDestroyView() {
    mediaPlayer.stop()
    mediaPlayer.release()
    disposables.clear()
    abandonAudioFocus()
    requireActivity().unregisterReceiver(playerMediaReceiver)
    super.onDestroyView()
  }

  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        if ((playOnAudioFocus || audioFocusDelayed) && !mediaPlayer.isPlaying) {
          audioFocusDelayed = false
          playOnAudioFocus = false
          startPlaying()
        }
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        if (mediaPlayer.isPlaying) {
          playOnAudioFocus = true
          audioFocusDelayed = false
          onPauseButtonPressed(
            abandonAudioFocus = false
          )
        }
      }
      AudioManager.AUDIOFOCUS_LOSS -> {
        audioFocusDelayed = false
        playOnAudioFocus = false
        onPauseButtonPressed(
          abandonAudioFocus = true
        )
      }
    }
  }

  private fun configureToolbar() {
    this.toolbar.setNavigationOnClickListener { requireActivity().finish() }
  }

  private fun configurePlayer() {
    this.covers.loadCoverInto(
      entry = feedEntry,
      hasBadge = false,
      imageView = previewCover,
      width = 0,
      height = 0
    )

    previewAuthor.text = feedEntry.feedEntry.authorsCommaSeparated
    previewTitle.text = feedEntry.feedEntry.title

    mediaPlayer = MediaPlayer()
    mediaPlayer.setDataSource(requireContext(), Uri.fromFile(audioFile))
    mediaPlayer.prepare()
    mediaPlayer.setOnPreparedListener {
      mediaPlayer.seekTo(0)
      previewSeekbar.max = mediaPlayer.duration
      previewSeekbar.setOnTouchListener { _, event -> handleTouchOnSeekbar(event) }
      updateRemainingTime(0)
      configurePlayerButtons()
      startTimer()
    }
    mediaPlayer.setOnCompletionListener {
      mediaPlayer.seekTo(0)
      onPauseButtonPressed(
        abandonAudioFocus = false
      )
    }
  }

  private fun startTimer() {
    disposables.add(
      Observable.interval(1000L, TimeUnit.MILLISECONDS)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {
          updatePlayerUI()
        }
    )
  }

  private fun updatePlayerUI() {
    if (!this.playerPositionDragging) {
      previewSeekbar.progress = mediaPlayer.currentPosition
    }

    val currentPosition = mediaPlayer.currentPosition.toLong()
    updateCurrentTime(currentPosition)
    updateRemainingTime(currentPosition)
  }

  private fun updateCurrentTime(currentPosition: Long) {
    previewCurrentTime.text =
      BookPreviewTimeUtils.hourMinuteSecondTextFromDuration(
        Duration.millis(currentPosition)
      )
    previewCurrentTime.contentDescription =
      this.playerTimeCurrentSpoken(currentPosition)
  }

  private fun updateRemainingTime(currentPosition: Long) {
    val duration = mediaPlayer.duration.toLong()

    previewRemainingTime.text =
      BookPreviewTimeUtils.hourMinuteSecondTextFromDuration(
        Duration.millis(duration - currentPosition)
      )
    previewRemainingTime.contentDescription =
      this.playerTimeRemainingSpoken(currentPosition, duration)
  }

  private fun onPauseButtonPressed(abandonAudioFocus: Boolean) {
    if (abandonAudioFocus) {
      abandonAudioFocus()
    }

    previewPlayPauseButton.setImageResource(R.drawable.play_icon)
    previewPlayPauseButton.contentDescription =
      getString(R.string.bookPreviewAccessibilityPlay)
    mediaPlayer.pause()
  }

  private fun onPlayButtonPressed() {
    when (requestAudioFocus()) {
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        this.logger.debug("Audio focus request granted")
        startPlaying()
      }
      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        this.logger.debug("Audio focus request delayed")
        audioFocusDelayed = true
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
      if (audioRequest != null) {
        audioManager.abandonAudioFocusRequest(audioRequest!!)
      }
    } else {
      audioManager.abandonAudioFocus(this)
    }
  }

  private fun requestAudioFocus(): Int {
    // initiate the audio playback attributes
    val playbackAttributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(playbackAttributes)
        .setWillPauseWhenDucked(true)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(this)
        .build()

      audioManager.requestAudioFocus(audioRequest!!)
    } else {
      audioManager.requestAudioFocus(
        this, AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN
      )
    }
  }

  private fun startPlaying() {
    previewPlayPauseButton.setImageResource(R.drawable.pause_icon)
    previewPlayPauseButton.contentDescription =
      this.getString(R.string.bookPreviewAccessibilityPause)
    mediaPlayer.start()
  }

  private fun configurePlayerButtons() {
    previewPlayPauseButton.setOnClickListener {
      if (mediaPlayer.isPlaying) {
        onPauseButtonPressed(
          abandonAudioFocus = true
        )
      } else {
        onPlayButtonPressed()
      }
    }

    previewForwardButton.setOnClickListener {
      val duration = mediaPlayer.duration
      val position = mediaPlayer.currentPosition + PLAYER_MOVE_THRESHOLD
      mediaPlayer.seekTo(
        if (position <= duration) {
          position.toInt()
        } else {
          duration
        }
      )
    }

    previewBackwardButton.setOnClickListener {
      val position = mediaPlayer.currentPosition - PLAYER_MOVE_THRESHOLD
      mediaPlayer.seekTo(
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
        clickedOnThumb = wasSeekbarThumbClicked(event)
        if (!clickedOnThumb) {
          return true
        }
      }
      MotionEvent.ACTION_UP -> {
        if (clickedOnThumb && playerPositionDragging) {
          playerPositionDragging = false
          clickedOnThumb = false
          updateUIOnProgressBarDragging()
        } else {
          playerPositionDragging = false
          clickedOnThumb = false
          return true
        }
      }
      MotionEvent.ACTION_MOVE -> {
        if (!clickedOnThumb) {
          return true
        }
        playerPositionDragging = true
        updateUIOnProgressBarDragging()
      }
      MotionEvent.ACTION_CANCEL -> {
        playerPositionDragging = false
        clickedOnThumb = false
      }
    }

    return previewSeekbar.onTouchEvent(event)
  }

  private fun wasSeekbarThumbClicked(event: MotionEvent): Boolean {
    return previewSeekbar.thumb.bounds.contains(event.x.toInt(), event.y.toInt())
  }

  private fun updateUIOnProgressBarDragging() {
    this.logger.debug("updateUIOnProgressBarDragging")

    mediaPlayer.seekTo(previewSeekbar.progress)
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
