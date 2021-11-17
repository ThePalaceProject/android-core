package org.nypl.simplified.ui.catalog

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import org.nypl.simplified.ui.screen.ScreenSizeInformationType

/**
 * Functions to create buttons for catalog views.
 */

class CatalogButtons(
  private val context: Context,
  private val screenSizeInformation: ScreenSizeInformationType
) {

  private fun colorStateListForButtonItems(): ColorStateList? {
    return ContextCompat.getColorStateList(context, R.color.simplified_button_text)
  }

  @UiThread
  fun createButtonSpace(): Space {
    val space = Space(this.context)
    space.layoutParams = this.buttonSpaceLayoutParameters()
    return space
  }

  @UiThread
  fun createCenteredTextForButtons(
    @StringRes res: Int
  ): TextView {
    val text = AppCompatTextView(this.context)
    text.gravity = Gravity.CENTER
    text.text = this.context.getString(res)
    return text
  }

  @UiThread
  fun createCenteredTextForButtons(
    centeredText: String
  ): TextView {
    return AppCompatTextView(this.context).apply {
      gravity = Gravity.CENTER
      text = centeredText
    }
  }

  @UiThread
  fun createButton(
    context: Context,
    text: Int,
    description: Int,
    heightMatchParent: Boolean = false,
    onClick: (Button) -> Unit
  ): Button {
    val button = AppCompatButton(this.context)
    button.text = context.getString(text)
    button.contentDescription = context.getString(description)
    button.layoutParams = this.buttonLayoutParameters(heightMatchParent)
    button.setOnClickListener {
      button.isEnabled = false
      onClick.invoke(button)
      button.isEnabled = true
    }
    return button
  }

  @UiThread
  fun createReadButtonWithLoanDuration(
    loanDuration: String,
    onClick: () -> Unit
  ): LinearLayout {
    return createButtonWithDuration(loanDuration, R.string.catalogRead, onClick)
  }

  @UiThread
  fun createDownloadButtonWithLoanDuration(
    loanDuration: String,
    onClick: () -> Unit
  ): LinearLayout {
    return createButtonWithDuration(loanDuration, R.string.catalogGet, onClick)
  }

  @UiThread
  fun createListenButtonWithLoanDuration(
    loanDuration: String,
    onClick: () -> Unit
  ): LinearLayout {
    return createButtonWithDuration(loanDuration, R.string.catalogListen, onClick)
  }

  @UiThread
  fun createButtonWithDuration(
    loanDuration: String,
    @StringRes res: Int,
    onClick: () -> Unit
  ): LinearLayout {

    val mainText = this.createCenteredTextForButtons(res).apply {
      this.layoutParams = wrapContentParameters()
      this.setTextColor(colorStateListForButtonItems())
      this.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      this.isDuplicateParentStateEnabled = true
      this.movementMethod = null
      this.isVerticalScrollBarEnabled = false
    }

    val textLoanDuration = this.createCenteredTextForButtons(loanDuration).apply {
      this.layoutParams = wrapContentParameters()
      this.setTextColor(colorStateListForButtonItems())
      this.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      this.isDuplicateParentStateEnabled = true
      this.movementMethod = null
      this.isVerticalScrollBarEnabled = false
    }

    val imageViewDimension = this.screenSizeInformation.dpToPixels(8).toInt()

    val imageView = AppCompatImageView(this.context).apply {
      this.layoutParams = ViewGroup.MarginLayoutParams(imageViewDimension, imageViewDimension).apply {
        this.bottomMargin = 4
      }
      this.isDuplicateParentStateEnabled = true
      this.setImageResource(R.drawable.ic_clock)
      this.imageTintList = colorStateListForButtonItems()
    }

    val linearLayout = LinearLayout(this.context).apply {
      this.orientation = LinearLayout.VERTICAL
      this.gravity = Gravity.CENTER
      this.layoutParams = ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      ).apply {
        this.marginEnd = screenSizeInformation.dpToPixels(6).toInt()
      }
      this.isDuplicateParentStateEnabled = true
      this.addView(imageView)
      this.addView(textLoanDuration)
    }

    return LinearLayout(this.context, null, androidx.appcompat.R.attr.buttonStyle).apply {
      this.orientation = LinearLayout.HORIZONTAL
      this.gravity = Gravity.CENTER
      this.layoutParams = buttonLayoutParameters(
        heightMatchParent = true
      )
      this.setOnClickListener {
        this.isEnabled = false
        onClick.invoke()
        this.isEnabled = true
      }
      this.addView(linearLayout)
      this.addView(mainText)
    }
  }

  @UiThread
  fun createReadButton(
    onClick: (Button) -> Unit,
    heightMatchParent: Boolean = false
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogRead,
      description = R.string.catalogAccessibilityBookRead,
      heightMatchParent = heightMatchParent,
      onClick = onClick
    )
  }

  @UiThread
  fun createListenButton(
    onClick: (Button) -> Unit,
    heightMatchParent: Boolean = false
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogListen,
      description = R.string.catalogAccessibilityBookListen,
      heightMatchParent = heightMatchParent,
      onClick = onClick
    )
  }

  @UiThread
  fun createDownloadButton(
    onClick: (Button) -> Unit,
    heightMatchParent: Boolean = false
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogGet,
      description = R.string.catalogAccessibilityBookBorrow,
      heightMatchParent = heightMatchParent,
      onClick = onClick
    )
  }

  @UiThread
  fun createRevokeHoldButton(
    onClick: (Button) -> Unit,
    heightMatchParent: Boolean = false
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogCancelHold,
      description = R.string.catalogAccessibilityBookRevokeHold,
      heightMatchParent = heightMatchParent,
      onClick = onClick
    )
  }

  @UiThread
  fun createRevokeLoanButton(
    onClick: (Button) -> Unit,
    heightMatchParent: Boolean = false
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogReturn,
      description = R.string.catalogAccessibilityBookRevokeLoan,
      heightMatchParent = heightMatchParent,
      onClick = onClick
    )
  }

  @UiThread
  fun createCancelDownloadButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogCancel,
      description = R.string.catalogAccessibilityBookDownloadCancel,
      onClick = onClick
    )
  }

  @UiThread
  fun createReserveButton(
    onClick: (Button) -> Unit,
    heightMatchParent: Boolean = false
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogReserve,
      description = R.string.catalogAccessibilityBookReserve,
      heightMatchParent = heightMatchParent,
      onClick = onClick
    )
  }

  @UiThread
  fun createGetButton(
    onClick: (Button) -> Unit,
    heightMatchParent: Boolean = false
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogGet,
      description = R.string.catalogAccessibilityBookBorrow,
      heightMatchParent = heightMatchParent,
      onClick = onClick
    )
  }

  @UiThread
  fun createDeleteButton(
    onClick: (Button) -> Unit,
    heightMatchParent: Boolean = false
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogDelete,
      description = R.string.catalogAccessibilityBookDelete,
      heightMatchParent = heightMatchParent,
      onClick = onClick
    )
  }

  @UiThread
  fun createRetryButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogRetry,
      description = R.string.catalogAccessibilityBookErrorRetry,
      onClick = onClick
    )
  }

  @UiThread
  fun createDetailsButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogDetails,
      description = R.string.catalogAccessibilityBookErrorDetails,
      onClick = onClick
    )
  }

  @UiThread
  fun createDismissButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogDismiss,
      description = R.string.catalogAccessibilityBookErrorDismiss,
      onClick = onClick
    )
  }

  @UiThread
  fun createButtonSizedSpace(): View {
    val space = Space(this.context)
    space.layoutParams = this.buttonLayoutParameters()
    space.visibility = View.INVISIBLE
    space.isEnabled = false
    return space
  }

  @UiThread
  fun buttonSpaceLayoutParameters(): LinearLayout.LayoutParams {
    val spaceLayoutParams = LinearLayout.LayoutParams(0, 0)
    spaceLayoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
    spaceLayoutParams.width = this.screenSizeInformation.dpToPixels(16).toInt()
    return spaceLayoutParams
  }

  @UiThread
  fun buttonLayoutParameters(heightMatchParent: Boolean = false): LinearLayout.LayoutParams {
    val buttonLayoutParams = LinearLayout.LayoutParams(0, 0)
    buttonLayoutParams.weight = 1.0f
    buttonLayoutParams.height = if (heightMatchParent) {
      LinearLayout.LayoutParams.MATCH_PARENT
    } else {
      LinearLayout.LayoutParams.WRAP_CONTENT
    }
    buttonLayoutParams.width = 0
    return buttonLayoutParams
  }

  @UiThread
  fun wrapContentParameters(): LinearLayout.LayoutParams {
    return LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    )
  }
}
