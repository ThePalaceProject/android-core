package org.nypl.simplified.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import org.librarysimplified.ui.R

class SettingsToggleView
  @JvmOverloads
  constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
  ) : FrameLayout(context, attrs, defStyleAttr) {
    private val icon: ImageView
    val toggle: CheckBox
    val textTitle: TextView
    val textSummary: TextView
    val root: View

    init {
      this.isClickable = true
      this.isFocusable = true

      val styledAttrs =
        context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
      val resid = styledAttrs.getResourceId(0, 0)
      styledAttrs.recycle()
      if (resid != 0) {
        this.background = AppCompatResources.getDrawable(context, resid)
      }

      this.root =
        LayoutInflater
          .from(context)
          .inflate(R.layout.settings_toggle_view, this, true)

      val a =
        context.theme.obtainStyledAttributes(
          attrs, R.styleable.SettingsToggleView, defStyleAttr, 0
        )

      try {
        val title =
          a.getString(R.styleable.SettingsToggleView_settingsToggleTitle)
        val summary =
          a.getString(R.styleable.SettingsToggleView_settingsToggleText)
        val iconRes =
          a.getResourceId(R.styleable.SettingsToggleView_settingsToggleIcon, 0)

        this.textTitle =
          this.findViewById(R.id.settingsToggleTitle)
        this.textSummary =
          this.findViewById(R.id.settingsToggleSummary)
        this.toggle =
          this.findViewById(R.id.settingsToggleCheckbox)
        this.icon =
          this.findViewById(R.id.settingsToggleIcon)

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

    override fun setOnClickListener(l: OnClickListener?) {
      super.setOnClickListener {
        this.toggle.isChecked = !this.toggle.isChecked
        l?.onClick(this)
      }
    }
  }
