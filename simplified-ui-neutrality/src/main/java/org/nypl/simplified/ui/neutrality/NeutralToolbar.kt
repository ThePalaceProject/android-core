package org.nypl.simplified.ui.neutrality

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.TextViewCompat
import org.nypl.simplified.ui.neutrality.NeutralToolbar.IconKind.ICON_IS_LOGO
import org.nypl.simplified.ui.neutrality.NeutralToolbar.IconKind.ICON_IS_NAVIGATION

/**
 * A toolbar with very neutral styling.
 */

class NeutralToolbar(
  context: Context,
  attrs: AttributeSet?,
  defStyleAttr: Int
) : Toolbar(context, attrs, defStyleAttr) {

  companion object {
    const val neutralToolbarName = "NeutralToolbar"
  }

  constructor(context: Context) : this(context, null)
  constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, androidx.appcompat.R.attr.toolbarStyle)

  private fun dpToPixelsIntegral(dp: Int): Int {
    return this.dpToPixelsReal(dp).toInt()
  }

  private fun dpToPixelsReal(dp: Int): Double {
    val scale = this.resources.displayMetrics.density
    return (dp * scale).toDouble() + 0.5
  }

  private var iconLogoLast: Drawable? = null
  private var iconKind: IconKind
  private val titleView: TextView = TextView(this.context)
  private val iconView: ImageView = ImageView(this.context)

  private enum class IconKind {
    ICON_IS_NAVIGATION,
    ICON_IS_LOGO
  }

  init {
    val iconDimension = this.dpToPixelsIntegral(64)

    this.iconKind = ICON_IS_LOGO
    TextViewCompat.setTextAppearance(this.titleView, R.style.Neutral_ActionBarTitle)
    this.titleView.apply {
      layoutParams = MarginLayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT
      ).apply {
        marginStart = iconDimension
      }
      gravity = Gravity.CENTER
    }
    this.addView(this.titleView)
    this.addView(this.iconView, LayoutParams(iconDimension, iconDimension))
    this.tag = neutralToolbarName
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)

    when (this.iconKind) {
      ICON_IS_NAVIGATION -> {
        val iconHeight = this.dpToPixelsReal(24).toFloat()
        this.iconView.x = this.dpToPixelsReal(16).toFloat()
        this.iconView.y = (this.height / 2.0f) - (iconHeight / 2.0f)
      }
      ICON_IS_LOGO -> {
        this.iconView.x = 0.0f
        this.iconView.y = 0.0f
      }
    }
  }

  /*
   * This method is called by [ActionBar.setHomeAsUpEnabled]. We ignore the drawable
   * that it passes in and use our own. The `setHomeAsUpEnabled` method will call this
   * method with a null drawable if the user passes `false` to `setHomeAsUpEnabled`,
   * and we translate that to a call that sets the previous logo image.
   */

  override fun setNavigationIcon(drawable: Drawable?) {
    if (drawable != null) {
      this.iconKind = ICON_IS_NAVIGATION
      this.iconView.setImageResource(R.drawable.ic_baseline_arrow_back_24)
      val iconWidth = this.dpToPixelsReal(24).toFloat()
      val iconHeight = this.dpToPixelsReal(24).toFloat()
      this.iconView.x = this.dpToPixelsReal(16).toFloat()
      this.iconView.y = (this.height / 2.0f) - (iconHeight / 2.0f)
      this.iconView.layoutParams = LayoutParams(iconWidth.toInt(), iconHeight.toInt())
      this.iconView.contentDescription = context.getString(R.string.contentDescriptionBack)
    } else {
      this.iconKind = ICON_IS_LOGO
      this.setLogo(this.iconLogoLast)
      this.iconView.contentDescription = context.getString(R.string.contentDescriptionLogo)
    }
  }

  /*
   * This method is called by [ActionBar.setIcon].
   */

  override fun setLogo(drawable: Drawable?) {
    this.iconKind = ICON_IS_LOGO
    this.iconLogoLast = drawable
    this.iconView.setImageDrawable(drawable)
    this.iconView.layoutParams = LayoutParams(this.height, this.height)
  }

  override fun setSubtitle(subtitle: CharSequence?) {
  }

  override fun setTitle(title: CharSequence) {
    this.titleView.text = title
  }

  fun setLogoOnClickListener(listener: () -> Unit) {
    this.iconView.setOnClickListener {
      listener()
    }
  }
}
