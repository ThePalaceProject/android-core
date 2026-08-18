package org.nypl.simplified.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import org.librarysimplified.ui.R

class SettingsToggleView
  @JvmOverloads
  constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
  ) : FrameLayout(context, attrs, defStyleAttr) {
    private val icon: ImageView
    val toggle: SwitchCompat
    val textTitle: TextView

    init {
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
        val text =
          a.getString(R.styleable.SettingsToggleView_settingsToggleText)
        val iconRes =
          a.getResourceId(R.styleable.SettingsToggleView_settingsToggleIcon, 0)

        this.textTitle =
          this.findViewById(R.id.settingsToggleTitle)
        this.toggle =
          this.findViewById(R.id.settingsToggleSwitch)
        this.icon =
          this.findViewById(R.id.settingsToggleIcon)

        this.textTitle.text = title
        this.toggle.text = text

        if (iconRes != 0) {
          this.icon.setImageResource(iconRes)
          this.icon.visibility = VISIBLE
        }
      } finally {
        a.recycle()
      }
    }
  }
