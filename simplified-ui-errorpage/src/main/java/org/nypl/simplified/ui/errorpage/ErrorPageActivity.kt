package org.nypl.simplified.ui.errorpage

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.librarysimplified.ui.errorpage.R
import org.nypl.simplified.ui.screen.ScreenEdgeToEdgeFix

/**
 * A convenient base activity used to show error pages.
 */

class ErrorPageActivity : AppCompatActivity(R.layout.error_host) {

  companion object {
    const val PARAMETER_ID =
      "org.nypl.simplified.ui.errorpage.ErrorPageBaseActivity.parameters"

    fun show(
      context: Activity,
      parameters: ErrorPageParameters
    ) {
      val intent = Intent(context, ErrorPageActivity::class.java)
      val bundle = Bundle().apply {
        this.putSerializable(PARAMETER_ID, parameters)
      }
      intent.putExtras(bundle)
      context.startActivity(intent)
    }
  }

  private lateinit var root: View
  private lateinit var parameters: ErrorPageParameters
  private lateinit var errorFragment: ErrorPageFragment

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val currentIntent =
      this.intent
        ?: throw IllegalStateException("No intent provided for activity")
    val currentExtras =
      currentIntent.extras
        ?: throw IllegalStateException("No extras provided for activity")
    val currentParameters =
      currentExtras.getSerializable(PARAMETER_ID)
        ?: throw IllegalStateException("No parameters provided for activity")

    this.parameters =
      if (currentParameters is ErrorPageParameters) {
        currentParameters
      } else {
        throw IllegalStateException("Parameters of wrong type provided for activity")
      }

    this.errorFragment = ErrorPageFragment.create(this.parameters)

    this.supportFragmentManager.beginTransaction()
      .replace(R.id.errorHolder, this.errorFragment, "ERROR_MAIN")
      .commit()

    this.root = this.findViewById(R.id.errorHolderRoot)
    ScreenEdgeToEdgeFix.edgeToEdge(this.root)
  }
}
