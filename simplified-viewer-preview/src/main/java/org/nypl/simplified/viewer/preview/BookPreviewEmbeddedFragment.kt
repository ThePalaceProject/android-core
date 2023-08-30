package org.nypl.simplified.viewer.preview

import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import org.nypl.simplified.webview.WebViewUtilities
import java.io.BufferedReader

class BookPreviewEmbeddedFragment : Fragment() {

  companion object {
    private const val BUNDLE_EXTRA_URL =
      "org.nypl.simplified.viewer.preview.BookPreviewEmbeddedFragment.url"

    fun newInstance(url: String): BookPreviewEmbeddedFragment {
      return BookPreviewEmbeddedFragment().apply {
        arguments = Bundle().apply {
          putString(BUNDLE_EXTRA_URL, url)
        }
      }
    }
  }

  private val playerMediaReceiver by lazy {
    BookPreviewPlayerMediaReceiver(
      onAudioBecomingNoisy = {
        pauseWebViewPlayer()
      }
    )
  }

  private lateinit var toolbar: Toolbar
  private lateinit var webView: WebView

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_book_preview_embedded, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.webView = view.findViewById(R.id.web_view)
    this.toolbar = view.findViewById(R.id.toolbar)

    configureToolbar()
    configureWebView()

    requireActivity().registerReceiver(
      playerMediaReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    )
  }

  override fun onDestroyView() {
    if (::webView.isInitialized) {
      webView.destroy()
    }
    requireActivity().unregisterReceiver(playerMediaReceiver)
    super.onDestroyView()
  }

  private fun configureToolbar() {
    this.toolbar.setNavigationOnClickListener {
      requireActivity().finish()
    }
    this.toolbar.setNavigationContentDescription(R.string.bookPreviewAccessibilityBack)
  }

  private fun configureWebView() {
    webView.apply {
      webViewClient = CustomWebViewClient()
      webChromeClient = CustomWebChromeClient()
      settings.allowFileAccess = true
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true

      WebViewUtilities.setForcedDark(settings, resources.configuration)

      loadUrl(requireArguments().getString(BUNDLE_EXTRA_URL).orEmpty())
    }
  }

  private fun pauseWebViewPlayer() {
    webView.evaluateJavascript("PalacePreviewPlayerCommands.pausePlayer()", null)
  }

  private inner class CustomWebViewClient : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
      injectJavaScript()
      super.onPageFinished(view, url)
    }

    private fun injectJavaScript() {
      try {
        val inputStream = requireActivity().assets.open("preview_player_commands.js")
        inputStream.bufferedReader().use(BufferedReader::readText)
      } catch (e: Exception) {
        null
      }?.let {
        val encodedString = Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP)
        webView.evaluateJavascript(
          "(function() {" +
            "const script = document.createElement('script');" +
            "script.innerHTML = decodeURIComponent(window.atob('" + encodedString + "'));" +
            "document.head.appendChild(script);" +
            "})()",
          null
        )
      }
    }
  }

  private inner class CustomWebChromeClient : WebChromeClient() {

    private val window = requireActivity().window
    private val windowInsetsController =
      WindowCompat.getInsetsController(window, window.decorView)

    private var fullscreenView: View? = null

    init {
      windowInsetsController?.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onHideCustomView() {
      fullscreenView?.isVisible = false
      webView.isVisible = true
      windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
      val decorView = requireActivity().window.decorView as? FrameLayout ?: return
      webView.isVisible = false

      if (fullscreenView != null) {
        decorView.removeView(fullscreenView)
      }

      fullscreenView = view

      decorView.addView(
        fullscreenView,
        FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.MATCH_PARENT
        )
      )

      fullscreenView?.isVisible = true
      windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())
    }
  }
}
