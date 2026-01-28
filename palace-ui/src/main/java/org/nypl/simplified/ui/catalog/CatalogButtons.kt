package org.nypl.simplified.ui.catalog

import android.content.Context
import android.widget.Button
import androidx.annotation.UiThread
import org.librarysimplified.ui.R

/**
 * Functions to create buttons for catalog views.
 */

class CatalogButtons(
  private val context: Context
) {
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
    button.setText(R.string.catalogManageHold)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookManageHold)
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
  fun setAsReserveButton(
    button: Button,
    onClick: (Button) -> Unit
  ): Button {
    button.setText(R.string.catalogPlaceHold)
    button.contentDescription = this.context.getString(R.string.catalogAccessibilityBookPlaceHold)
    button.setOnClickListener { onClick.invoke(button) }
    return button
  }
}
