package org.nypl.simplified.ui.errorpage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.librarysimplified.ui.errorpage.R
import org.nypl.simplified.taskrecorder.api.TaskStep
import org.nypl.simplified.taskrecorder.api.TaskStepResolution

/**
 * An adapter for the task steps recycler view.
 */

class ErrorPageStepsListAdapter(private val steps: List<TaskStep>) :
  RecyclerView.Adapter<ErrorPageStepsListAdapter.ViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val inflater =
      LayoutInflater.from(parent.context)
    val item =
      inflater.inflate(R.layout.error_step, parent, false)

    return this.ViewHolder(item)
  }

  override fun getItemCount(): Int =
    this.steps.size

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val step = this.steps[position]

    val resolution = step.resolution
    if (resolution is TaskStepResolution.TaskStepFailed) {
      holder.icon.setImageResource(R.drawable.error_small)

      val multiline = StringBuilder()
      multiline.append(resolution.message)
      for (extra in resolution.extraMessages) {
        multiline.append('\n')
        multiline.append(extra)
      }

      holder.resolution.text = multiline.toString()
    } else {
      holder.icon.setImageResource(R.drawable.ok_small)
      holder.resolution.text = resolution.message
    }

    holder.stepNumber.text = String.format("%d.", position + 1)
    holder.description.text = step.description
  }

  inner class ViewHolder(parent: View) : RecyclerView.ViewHolder(parent) {
    val icon =
      parent.findViewById<ImageView>(R.id.errorItemIcon)
    val description =
      parent.findViewById<TextView>(R.id.errorItemDescription)
    val resolution =
      parent.findViewById<TextView>(R.id.errorItemResolution)
    val stepNumber =
      parent.findViewById<TextView>(R.id.errorItemStepNumber)
  }
}
