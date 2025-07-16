package org.nypl.simplified.ui.images

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/*
 * An image view that has a fixed height but that will resize its own width to respect the
 * aspect ratio of an image.
 */

class ImageFlexibleWidthView : AppCompatImageView {
  constructor(context: Context) : super(context)

  constructor(
    context: Context,
    attrs: AttributeSet?
  ) : super(context, attrs)

  constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyle: Int
  ) : super(context, attrs, defStyle)

  override fun onMeasure(
    widthMeasureSpec: Int,
    heightMeasureSpec: Int
  ) {
    val drawable = drawable
    if (drawable != null) {
      val height = MeasureSpec.getSize(heightMeasureSpec)
      val intrinsicWidth = drawable.intrinsicWidth
      val intrinsicHeight = drawable.intrinsicHeight

      val aspect = intrinsicWidth.toFloat() / intrinsicHeight.toFloat()
      val width = (height * aspect).toInt()

      setMeasuredDimension(width, height)
    } else {
      super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
  }
}
