package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.librarysimplified.ui.R
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType
import org.nypl.simplified.ui.settings.SettingsDocumentViewerModel.DocumentTarget
import org.nypl.simplified.webview.WebViewUtilities

class SettingsDocumentViewerFragment : Fragment() {

  private lateinit var toolbarBack: View
  private lateinit var toolbarTitle: TextView
  private lateinit var documentWebView: WebView

  companion object :
    ScreenDefinitionFactoryType<DocumentTarget, SettingsDocumentViewerFragment> {
    private class ScreenSettingsDocument(
      private val documentTarget: DocumentTarget
    ) : ScreenDefinitionType<DocumentTarget, SettingsDocumentViewerFragment> {
      override fun setup() {
        SettingsDocumentViewerModel.documentTarget = this.documentTarget
      }

      override fun parameters(): DocumentTarget {
        return this.documentTarget
      }

      override fun fragment(): SettingsDocumentViewerFragment {
        return SettingsDocumentViewerFragment()
      }
    }

    override fun createScreenDefinition(
      p: DocumentTarget
    ): ScreenDefinitionType<DocumentTarget, SettingsDocumentViewerFragment> {
      return ScreenSettingsDocument(p)
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view =
      inflater.inflate(R.layout.settings_document_viewer, container, false)

    this.toolbarBack =
      view.findViewById(R.id.documentViewerToolbarBackIconTouch)
    this.toolbarTitle =
      view.findViewById(R.id.documentViewerToolbarTitle)
    this.documentWebView =
      view.findViewById(R.id.documentViewerWebView)

    return view
  }

  override fun onStart() {
    super.onStart()

    this.toolbarBack.setOnClickListener {
      this.toolbarBack.postDelayed(MainNavigation.Settings::goUp, 500)
    }

    val target = SettingsDocumentViewerModel.documentTarget
    if (target != null) {
      this.toolbarTitle.text = target.title
      this.documentWebView.webViewClient = WebViewClient()
      this.documentWebView.webChromeClient = WebChromeClient()
      this.documentWebView.settings.allowFileAccess = true
      WebViewUtilities.setForcedDark(this.documentWebView.settings, resources.configuration)
      this.documentWebView.loadUrl(target.url)
    } else {
      this.toolbarBack.post(MainNavigation.Settings::goUp)
    }
  }
}
