package org.librarysimplified.sandbox

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.librarysimplified.ui.bottomsheet.PalaceBottomSheet
import org.librarysimplified.ui.bottomsheet.PalaceBottomSheetType

class SandboxActivity : AppCompatActivity(R.layout.sandbox) {
  private lateinit var red: View
  private lateinit var bottomSheet: PalaceBottomSheet

  override fun onCreate(
    savedInstanceState: Bundle?
  ) {
    super.onCreate(savedInstanceState)

    this.bottomSheet =
      this.findViewById(R.id.sandboxBottomSheet)
    this.red =
      this.findViewById(R.id.sandboxTabRed)

    this.red.alpha = 0.0f
  }

  override fun onStart() {
    super.onStart()

    this.bottomSheet.drawerCloseInstantly()
    this.bottomSheet.setOpenListener(object : PalaceBottomSheetType.SheetOpenListenerType {
      override fun onOpenChanged(state: Double) {
        this@SandboxActivity.red.alpha = state.toFloat()
      }
    })
  }
}
