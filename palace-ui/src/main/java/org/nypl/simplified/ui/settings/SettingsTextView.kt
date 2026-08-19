package org.nypl.simplified.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.librarysimplified.ui.R

class SettingsTextView
  @JvmOverloads
  constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
  ) : FrameLayout(context, attrs, defStyleAttr) {
    private val icon: ImageView
    val textSummary: TextView
    val textTitle: TextView
    val root: View

    init {
      this.isClickable = false
      this.isFocusable = false

      this.root =
        LayoutInflater
          .from(context)
          .inflate(R.layout.settings_text_view, this, true)

      val a =
        context.theme.obtainStyledAttributes(
          attrs, R.styleable.SettingsTextView, defStyleAttr, 0
        )

      try {
        val title =
          a.getString(R.styleable.SettingsTextView_settingsTextTitle)
        val summary =
          a.getString(R.styleable.SettingsTextView_settingsTextSummary)
        val iconRes =
          a.getResourceId(R.styleable.SettingsTextView_settingsTextIcon, 0)

        this.textTitle =
          this.findViewById(R.id.settingsTextTitle)
        this.textSummary =
          this.findViewById(R.id.settingsTextSummary)
        this.icon =
          this.findViewById(R.id.settingsTextIcon)

        this.textTitle.text = title
        this.textSummary.text = summary

        if (summary.isNullOrEmpty()) {
          this.textSummary.visibility = View.GONE
        }

        if (iconRes != 0) {
          this.icon.setImageResource(iconRes)
          this.icon.visibility = VISIBLE
        }
      } finally {
        a.recycle()
      }
    }
  }
