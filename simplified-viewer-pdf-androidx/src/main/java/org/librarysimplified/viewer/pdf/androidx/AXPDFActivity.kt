package org.librarysimplified.viewer.pdf.androidx

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AXPDFActivity : AppCompatActivity() {
  override fun onCreate(
    savedInstanceState: Bundle?
  ) {
    super.onCreate(savedInstanceState)
    this.setContentView(R.layout.pdf_reader)
  }
}
