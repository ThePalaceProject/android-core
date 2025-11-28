package org.nypl.simplified.ui.announcements

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import org.librarysimplified.ui.R

class AnnouncementsDialog : DialogFragment(R.layout.announcements_dialog) {

  private lateinit var title: TextView
  private lateinit var content: TextView
  private lateinit var okButton: Button

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(view, savedInstanceState)

    this.title =
      view.findViewById(R.id.announcements_title)
    this.okButton =
      view.findViewById(R.id.announcements_ok)
    this.content =
      view.findViewById(R.id.announcements_content)
  }

  override fun onStart() {
    super.onStart()

    this.dialog?.window?.setLayout(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.WRAP_CONTENT
    )

    try {
      val announcements =
        AnnouncementsModel.announcements.get()

      if (announcements.isEmpty()) {
        this.dismiss()
        return
      }

      val firstID =
        announcements.firstKey()
      val announcement =
        announcements[firstID]

      if (announcement == null) {
        this.dismiss()
        return
      }

      val title =
        this.requireContext().getString(
          R.string.announcementTitle,
          announcement.providerTitle,
          announcement.index,
          announcement.count
        )

      this.title.text = title
      this.content.text = announcement.announcement.content

      this.okButton.setOnClickListener {
        AnnouncementsModel.acknowledge(announcement.announcement.id)
        this.dismiss()
      }
    } catch (e: Throwable) {
      this.dismiss()
    }
  }
}
