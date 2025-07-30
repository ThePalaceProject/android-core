package org.nypl.simplified.ui.catalog

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import androidx.annotation.UiThread
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
  fun setAsReadButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogRead)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookRead)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsReadPreviewButton(
    button: Button,
    bookFormat: BookFormats.BookFormatDefinition?,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(
      if (bookFormat == BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO) {
        R.string.catalogBookPreviewAudioBook
      } else {
        R.string.catalogBookPreviewBook
      }
    )
    button.contentDescription =
      if (bookFormat == BookFormats.BookFormatDefinition.BOOK_FORMAT_AUDIO) {
        context.getString(R.string.catalogAccessibilityBookPreviewPlay)
      } else {
        context.getString(R.string.catalogAccessibilityBookPreviewRead)
      }
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsListenButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogListen)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookListen)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsBorrowButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogGet)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookBorrow)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsDownloadButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogDownload)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookDownload)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsRevokeHoldButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogCancelHold)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookRevokeHold)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsRevokeLoanButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogReturn)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookRevokeLoan)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsCancelDownloadButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogCancel)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookDownloadCancel)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsReserveButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogReserve)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookReserve)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsRetryButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogRetry)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookErrorRetry)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsDetailsButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogDetails)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookErrorDetails)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }

  @UiThread
  fun setAsDismissButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogDismiss)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookErrorDismiss)
    button.setOnClickListener { onClick.invoke(button) }
    return button
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
}
