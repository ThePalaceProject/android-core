package org.nypl.simplified.ui.catalog

import android.content.Context
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
import org.nypl.simplified.ui.screen.ScreenSizeInformationType

/**
 * Functions to create buttons for catalog views.
 */

class CatalogButtons(
  private val context: Context,
  private val screenSizeInformation: ScreenSizeInformationType
) {

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
    onClick: (Button) -> Unit
  ): Button {
    val button = AppCompatButton(this.context)
    button.text = context.getString(text)
    button.contentDescription = context.getString(description)
    button.layoutParams = this.buttonLayoutParameters()
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
    return createButtonWithDuration(loanDuration, R.string.catalogDownload, onClick)
  }

  @UiThread
  fun createButtonWithDuration(
    loanDuration: String,
    @StringRes res: Int,
    onClick: () -> Unit
  ): LinearLayout {
    val textRead = this.createCenteredTextForButtons(res).apply {
      this.layoutParams = wrapContentParameters()
    }

    val textLoanDuration = this.createCenteredTextForButtons(loanDuration).apply {
      this.layoutParams = wrapContentParameters()
      this.textSize = 12f
    }

    val imageView = AppCompatImageView(this.context).apply {
      this.layoutParams = ViewGroup.MarginLayoutParams(25, 25).apply {
        this.bottomMargin = 4
      }
      this.setImageResource(R.drawable.ic_clock)
    }

    val linearLayout = LinearLayout(this.context).apply {
      this.orientation = LinearLayout.VERTICAL
      this.gravity = Gravity.CENTER
      this.layoutParams = ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      ).apply {
        this.marginEnd = screenSizeInformation.dpToPixels(16).toInt()
      }
      this.addView(imageView)
      this.addView(textLoanDuration)
    }

    return LinearLayout(this.context, null, androidx.appcompat.R.attr.buttonStyle).apply {
      this.orientation = LinearLayout.HORIZONTAL
      this.gravity = Gravity.CENTER
      this.layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.MATCH_PARENT
      )
      this.setOnClickListener {
        this.isEnabled = false
        onClick.invoke()
        this.isEnabled = true
      }
      this.addView(linearLayout)
      this.addView(textRead)
    }
  }

  @UiThread
  fun createReadButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogRead,
      description = R.string.catalogAccessibilityBookRead,
      onClick = onClick
    )
  }

  @UiThread
  fun createListenButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogListen,
      description = R.string.catalogAccessibilityBookListen,
      onClick = onClick
    )
  }

  @UiThread
  fun createDownloadButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogDownload,
      description = R.string.catalogAccessibilityBookDownload,
      onClick = onClick
    )
  }

  @UiThread
  fun createRevokeHoldButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogCancelHold,
      description = R.string.catalogAccessibilityBookRevokeHold,
      onClick = onClick
    )
  }

  @UiThread
  fun createRevokeLoanButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogReturn,
      description = R.string.catalogAccessibilityBookRevokeLoan,
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
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogReserve,
      description = R.string.catalogAccessibilityBookReserve,
      onClick = onClick
    )
  }

  @UiThread
  fun createGetButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogGet,
      description = R.string.catalogAccessibilityBookBorrow,
      onClick = onClick
    )
  }

  @UiThread
  fun createDeleteButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      context = this.context,
      text = R.string.catalogDelete,
      description = R.string.catalogAccessibilityBookDelete,
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
  fun createButtonSizedSpace(): View? {
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
  fun buttonLayoutParameters(): LinearLayout.LayoutParams {
    val buttonLayoutParams = LinearLayout.LayoutParams(0, 0)
    buttonLayoutParams.weight = 1.0f
    buttonLayoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT
    buttonLayoutParams.width = this.screenSizeInformation.dpToPixels(80).toInt()
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
