package org.nypl.simplified.ui.accounts.saml20

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.librarysimplified.ui.accounts.R

class AccountSAML20Activity : AppCompatActivity(R.layout.saml20_host) {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (savedInstanceState == null) {
      this.supportFragmentManager.beginTransaction()
        .replace(R.id.mainFragmentHolder, AccountSAML20Fragment())
        .commit()
    }
  }
}
