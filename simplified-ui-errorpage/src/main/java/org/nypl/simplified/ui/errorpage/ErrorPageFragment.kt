package org.nypl.simplified.ui.errorpage

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.librarysimplified.reports.Reports
import org.librarysimplified.ui.errorpage.R

/**
 * A full-screen fragment for displaying presentable errors, and reporting those errors
 * to technical support.
 */

class ErrorPageFragment : Fragment(R.layout.error_page) {

  private lateinit var errorDetails: TextView
  private lateinit var errorStepsList: RecyclerView
  private lateinit var sendButton: Button

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.errorDetails =
      view.findViewById(R.id.errorDetails)
    this.errorStepsList =
      view.findViewById(R.id.errorSteps)
    this.sendButton =
      view.findViewById(R.id.errorSendButton)

    val parameters = ErrorPageModel.parameters
    if (parameters.attributes.isEmpty()) {
      this.errorDetails.visibility = View.GONE
    } else {
      this.errorDetails.text = ""

      val ssb = SpannableStringBuilder()
      parameters.attributes.entries.forEachIndexed { index, (key, value) ->
        if (index > 0) { ssb.append("\n\n") }
        ssb.append(key)

        val styleSpan = StyleSpan(Typeface.BOLD)
        val spanStart = ssb.length - key.length
        val spanEnd = ssb.length
        ssb.setSpan(styleSpan, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        ssb.append("\n")
        ssb.append(value)
      }
      this.errorDetails.text = ssb
    }

    this.errorStepsList.setHasFixedSize(false)
    this.errorStepsList.layoutManager =
      LinearLayoutManager(context)
    this.errorStepsList.adapter =
      ErrorPageStepsListAdapter(parameters.taskSteps)
    (this.errorStepsList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
  }

  override fun onStart() {
    super.onStart()

    this.sendButton.isEnabled = true
    this.sendButton.setOnClickListener {
      this.sendButton.isEnabled = false

      val parameters = ErrorPageModel.parameters
      Reports.sendReportsDefault(
        context = requireContext(),
        address = parameters.emailAddress,
        body = parameters.report
      )
    }
  }
}
