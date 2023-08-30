package org.nypl.simplified.ui.accounts.view_bindings

import java.util.concurrent.atomic.AtomicBoolean

abstract class Base : AccountAuthenticationViewBindings() {
  private val cleared = AtomicBoolean(false)

  protected abstract fun clearActual()

  override fun clear() {
    if (this.cleared.compareAndSet(false, true)) {
      this.clearActual()
    }
  }
}
