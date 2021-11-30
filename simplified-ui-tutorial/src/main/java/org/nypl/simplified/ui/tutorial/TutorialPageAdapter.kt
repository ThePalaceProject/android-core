package org.nypl.simplified.ui.tutorial

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class TutorialPageAdapter : RecyclerView.Adapter<TutorialPageAdapter.TutorialPageViewHolder>() {

  private val images = arrayOf(R.drawable.background_image_tutorial1,
    R.drawable.background_image_tutorial2,
    R.drawable.background_image_tutorial3)

  override fun getItemCount(): Int {
    return this.images.size
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TutorialPageViewHolder {
    return TutorialPageViewHolder(LayoutInflater.from(parent.context).inflate(
      R.layout.view_tutorial_page, parent, false))
  }

  override fun onBindViewHolder(holder: TutorialPageViewHolder, position: Int) {
    holder.bind(images[position])
  }

  inner class TutorialPageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(imageResource: Int) {
      (itemView as ImageView).setImageResource(imageResource)
    }
  }
}
