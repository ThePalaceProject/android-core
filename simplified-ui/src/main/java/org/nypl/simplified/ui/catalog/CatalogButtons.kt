package org.nypl.simplified.ui.catalog

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.button.MaterialButton
import org.librarysimplified.ui.R
import org.nypl.simplified.books.book_database.api.BookFormats
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
    text: Int,
    description: Int,
    heightMatchParent: Boolean = false,
    onClick: (Button) -> Unit
  ): Button {
    val button = MaterialButton(this.context)
    button.text = this.context.getString(text)
    button.contentDescription = this.context.getString(description)
    button.layoutParams = this.buttonLayoutParameters(heightMatchParent)
    button.maxLines = 1
    button.ellipsize = TextUtils.TruncateAt.END
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
  ): View {
    return createButtonWithDuration(loanDuration, R.string.catalogRead, onClick)
  }

  @UiThread
  fun createDownloadButtonWithLoanDuration(
    loanDuration: String,
    onClick: () -> Unit
  ): View {
    return createButtonWithDuration(loanDuration, R.string.catalogDownload, onClick)
  }

  @UiThread
  fun createListenButtonWithLoanDuration(
    loanDuration: String,
    onClick: () -> Unit
  ): View {
    return createButtonWithDuration(loanDuration, R.string.catalogListen, onClick)
  }

  @UiThread
  fun createButtonWithDuration(
    loanDuration: String,
    @StringRes res: Int,
    onClick: () -> Unit
  ): View {
    val button = MaterialButton(this.context)
    button.text = this.context.getString(res)
    button.contentDescription = this.context.getString(res)
    button.layoutParams = this.buttonLayoutParameters(true)
    button.maxLines = 1
    button.ellipsize = TextUtils.TruncateAt.END
    button.setOnClickListener {
      button.isEnabled = false
      onClick.invoke()
      button.isEnabled = true
    }
    button.iconSize = this.screenSizeInformation.dpToPixels(24).toInt()
    button.icon = CatalogTimedLoanDrawable(
      context = this.context,
      screenSizeInformation = this.screenSizeInformation,
      durationText = loanDuration
    )
    return button
  }

  @UiThread
  fun createReadButton(
    onClick: (Button) -> Unit,
    heightMatchParent: Boolean = false
  ): Button {
    return this.createButton(
      text = R.string.catalogRead,
      description = R.string.catalogAccessibilityBookRead,
      heightMatchParent = heightMatchParent,
      onClick = onClick
    )
  }

  @UiThread
  fun createReadPreviewButton(
    bookFormat: BookFormats.BookFormatDefinition?,
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
      text = if (bookFormat == BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO) {
        R.string.catalogBookPreviewAudioBook
      } else {
        R.string.catalogBookPreviewBook
      },
      description = if (bookFormat == BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO) {
        R.string.catalogAccessibilityBookPreviewPlay
      } else {
        R.string.catalogAccessibilityBookPreviewRead
      },
      onClick = onClick
    )
  }

  @UiThread
  fun createListenButton(
    onClick: (Button) -> Unit,
    heightMatchParent: Boolean = false
  ): Button {
    return this.createButton(
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
      text = R.string.catalogDownload,
      description = R.string.catalogAccessibilityBookDownload,
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
      text = R.string.catalogGet,
      description = R.string.catalogAccessibilityBookBorrow,
      heightMatchParent = heightMatchParent,
      onClick = onClick
    )
  }

  @UiThread
  fun createRetryButton(
    onClick: (Button) -> Unit
  ): Button {
    return this.createButton(
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
