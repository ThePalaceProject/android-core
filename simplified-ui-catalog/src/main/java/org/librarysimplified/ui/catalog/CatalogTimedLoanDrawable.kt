package org.librarysimplified.ui.catalog

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat.OPAQUE
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import org.nypl.simplified.ui.screen.ScreenSizeInformationType

class CatalogTimedLoanDrawable(
  private val context: Context,
  private val screenSizeInformation: ScreenSizeInformationType,
  private val durationText: String
) : Drawable() {

  private val size: Int =
    this.screenSizeInformation.dpToPixels(12).toInt()
  private val clock: Drawable =
    AppCompatResources.getDrawable(this.context, R.drawable.ic_clock)!!
  private val paint: Paint =
    Paint(Paint.ANTI_ALIAS_FLAG)

  init {
    this.paint.textSize = 16.0f
  }

  override fun draw(canvas: Canvas) {
    val color = this.context.getColor(org.thepalaceproject.theme.core.R.color.PalaceTextColor)
    this.paint.color = color
    this.paint.textSize = 16.0f

    /*
     * Arrange the clock drawable such that it appears centered at the top of the icon.
     */

    val clockX = this.bounds.centerX() - (this.size / 2.0f)
    val clockY = 1.0f

    /*
     * Arrange the text such that it appears centered towards the bottom of the icon.
     */

    val textX = this.bounds.centerX() - (this.paint.measureText(this.durationText) / 2.0f)
    val textY = this.bounds.height() - 6.0f

    this.clock.bounds = Rect(0, 0, this.size, this.size)
    this.clock.setTint(color)

    canvas.save()
    canvas.translate(clockX, clockY)
    this.clock.draw(canvas)

    canvas.restore()
    canvas.translate(textX, textY)
    canvas.drawText(this.durationText, 0.0f, 0.0f, this.paint)
  }

  override fun setAlpha(alpha: Int) {
    // Unused
  }

  override fun setColorFilter(colorFilter: ColorFilter?) {
// Unused
  }

  @Deprecated("Deprecated in Java")
  override fun getOpacity(): Int {
    return OPAQUE
  }
}
