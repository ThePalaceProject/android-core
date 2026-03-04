package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import org.librarysimplified.http.api.LSHTTPNetworkAccessType
import org.librarysimplified.services.api.Services
import org.librarysimplified.ui.R
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType

class SettingsDebugMenuNetworkFragment : Fragment(R.layout.debug_network),
  MainBackButtonConsumerType {

  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugMenuNetworkFragment> {
    private class ScreenSettingsDebugMenu :
      ScreenDefinitionType<Unit, SettingsDebugMenuNetworkFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): SettingsDebugMenuNetworkFragment {
        return SettingsDebugMenuNetworkFragment()
      }
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugMenuNetworkFragment> {
      return ScreenSettingsDebugMenu()
    }
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }

  private var subscriptions: CloseableCollectionType<*> = CloseableCollection.create()
  private lateinit var debugNetworkCellularAvailable: TextView
  private lateinit var debugNetworkCellularPermitted: TextView
  private lateinit var debugNetworkWIFIAvailable: TextView
  private lateinit var debugNetworkWIFIPermitted: TextView

  private lateinit var toolbarBack: View

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.debugNetworkWIFIPermitted =
      view.findViewById(R.id.debugNetworkWIFIPermitted)
    this.debugNetworkWIFIAvailable =
      view.findViewById(R.id.debugNetworkWIFIAvailable)
    this.debugNetworkCellularPermitted =
      view.findViewById(R.id.debugNetworkCellularPermitted)
    this.debugNetworkCellularAvailable =
      view.findViewById(R.id.debugNetworkCellularAvailable)

    this.toolbarBack = view.findViewById(R.id.debugToolbarBackIconTouch)
    this.toolbarBack.setOnClickListener {
      this.onBackButtonPressed()
    }
  }

  override fun onStart() {
    super.onStart()

    val networkAccess =
      Services.serviceDirectory()
        .requireService(LSHTTPNetworkAccessType::class.java)

    this.subscriptions = CloseableCollection.create()
    this.subscriptions.add(
      networkAccess.cellularPermitted.subscribe { _, now ->
        UIThread.runOnUIThread {
          try {
            if (now) {
              this.debugNetworkCellularPermitted.text = "Downloads on cellular are PERMITTED ✔"
            } else {
              this.debugNetworkCellularPermitted.text = "Downloads on cellular are DENIED ❌"
            }
          } catch (e: Throwable) {
            // Don't care
          }
        }
      }
    )
    this.subscriptions.add(
      networkAccess.cellularAvailable.subscribe { _, now ->
        UIThread.runOnUIThread {
          try {
            if (now) {
              this.debugNetworkCellularAvailable.text = "Cellular is AVAILABLE ✔"
            } else {
              this.debugNetworkCellularAvailable.text = "Cellular is UNAVAILABLE ❌"
            }
          } catch (e: Throwable) {
            // Don't care
          }
        }
      }
    )
    this.subscriptions.add(
      networkAccess.wifiPermitted.subscribe { _, now ->
        UIThread.runOnUIThread {
          try {
            if (now) {
              this.debugNetworkWIFIPermitted.text = "Downloads on WIFI are PERMITTED ✔"
            } else {
              this.debugNetworkWIFIPermitted.text = "Downloads on WIFI are DENIED ❌"
            }
          } catch (e: Throwable) {
            // Don't care
          }
        }
      }
    )
    this.subscriptions.add(
      networkAccess.wifiAvailable.subscribe { _, now ->
        UIThread.runOnUIThread {
          try {
            if (now) {
              this.debugNetworkWIFIAvailable.text = "WIFI is AVAILABLE ✔"
            } else {
              this.debugNetworkWIFIAvailable.text = "WIFI is UNAVAILABLE ❌"
            }
          } catch (e: Throwable) {
            // Don't care
          }
        }
      }
    )
  }

  override fun onStop() {
    super.onStop()
    this.subscriptions.close()
  }
}
