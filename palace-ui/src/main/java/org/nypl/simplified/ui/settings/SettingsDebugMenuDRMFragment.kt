package org.nypl.simplified.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.io7m.jmulticlose.core.CloseableCollection
import com.io7m.jmulticlose.core.CloseableCollectionType
import org.librarysimplified.ui.R
import org.nypl.simplified.adobe.extensions.AdobeDRMExtensions
import org.nypl.simplified.ui.main.MainBackButtonConsumerType
import org.nypl.simplified.ui.main.MainBackButtonConsumerType.Result.BACK_BUTTON_CONSUMED
import org.nypl.simplified.ui.main.MainNavigation
import org.nypl.simplified.ui.screens.ScreenDefinitionFactoryType
import org.nypl.simplified.ui.screens.ScreenDefinitionType

class SettingsDebugMenuDRMFragment : Fragment(R.layout.debug_drm), MainBackButtonConsumerType {

  companion object : ScreenDefinitionFactoryType<Unit, SettingsDebugMenuDRMFragment> {
    private class ScreenSettingsDebugMenu :
      ScreenDefinitionType<Unit, SettingsDebugMenuDRMFragment> {
      override fun setup() {
        // No setup required
      }

      override fun parameters() {
        return Unit
      }

      override fun fragment(): SettingsDebugMenuDRMFragment {
        return SettingsDebugMenuDRMFragment()
      }
    }

    override fun createScreenDefinition(p: Unit): ScreenDefinitionType<Unit, SettingsDebugMenuDRMFragment> {
      return ScreenSettingsDebugMenu()
    }
  }

  override fun onBackButtonPressed(): MainBackButtonConsumerType.Result {
    MainNavigation.Settings.goUp()
    return BACK_BUTTON_CONSUMED
  }

  private lateinit var adobeDRMActivationTable: TableLayout
  private lateinit var drmTable: TableLayout
  private var subscriptions: CloseableCollectionType<*> =
    CloseableCollection.create()

  private lateinit var toolbarBack: View

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.toolbarBack = view.findViewById(R.id.debugToolbarBackIconTouch)
    this.toolbarBack.setOnClickListener {
      this.onBackButtonPressed()
    }

    this.drmTable =
      view.findViewById(R.id.debugDrmSupport)
    this.adobeDRMActivationTable =
      view.findViewById(R.id.debugDrmAdobeActivations)

    this.drmTable.addView(
      this.createDrmSupportRow("Adobe Acs", SettingsDebugModel.adeptSupported())
    )
    this.drmTable.addView(
      this.createDrmSupportRow("Boundless", SettingsDebugModel.boundlessSupported())
    )
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CloseableCollection.create()
    this.subscriptions.add(
      SettingsDebugModel.adeptActivations.subscribe { _, activations ->
        this.onAdobeDRMReceivedActivations(activations)
      }
    )

    SettingsDebugModel.fetchAdobeActivations()
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.close()
  }

  private fun createDrmSupportRow(
    name: String,
    isSupported: Boolean
  ): TableRow {
    val row =
      this.layoutInflater.inflate(
        R.layout.settings_version_table_item, this.drmTable, false
      ) as TableRow
    val key =
      row.findViewById<TextView>(R.id.key)
    val value =
      row.findViewById<TextView>(R.id.value)

    key.text = name

    if (isSupported) {
      value.setTextColor(Color.GREEN)
      value.text = "Supported"
    } else {
      value.setTextColor(Color.RED)
      value.text = "Unsupported"
    }

    return row
  }

  private fun onAdobeDRMReceivedActivations(
    activations: List<AdobeDRMExtensions.Activation>
  ) {
    this.adobeDRMActivationTable.removeAllViews()

    this.run {
      val row =
        this.layoutInflater.inflate(
          R.layout.settings_drm_activation_table_item,
          this.adobeDRMActivationTable,
          false
        ) as TableRow
      val index = row.findViewById<TextView>(R.id.index)
      val vendor = row.findViewById<TextView>(R.id.vendor)
      val device = row.findViewById<TextView>(R.id.device)
      val userName = row.findViewById<TextView>(R.id.userName)
      val userId = row.findViewById<TextView>(R.id.userId)
      val expiry = row.findViewById<TextView>(R.id.expiry)

      index.text = "Index"
      vendor.text = "Vendor"
      device.text = "Device"
      userName.text = "UserName"
      userId.text = "UserID"
      expiry.text = "Expiry"

      this.adobeDRMActivationTable.addView(row)
    }

    for (activation in activations) {
      val row =
        this.layoutInflater.inflate(
          R.layout.settings_drm_activation_table_item,
          this.adobeDRMActivationTable,
          false
        ) as TableRow
      val index = row.findViewById<TextView>(R.id.index)
      val vendor = row.findViewById<TextView>(R.id.vendor)
      val device = row.findViewById<TextView>(R.id.device)
      val userName = row.findViewById<TextView>(R.id.userName)
      val userId = row.findViewById<TextView>(R.id.userId)
      val expiry = row.findViewById<TextView>(R.id.expiry)

      index.text = activation.index.toString()
      vendor.text = activation.vendor.value
      device.text = activation.device.value
      userName.text = activation.userName
      userId.text = activation.userID.value
      expiry.text = activation.expiry ?: "No expiry"

      this.adobeDRMActivationTable.addView(row)
    }
  }
}
