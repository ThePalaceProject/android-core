package org.nypl.simplified.tests.books.book_database

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.nypl.simplified.books.book_database.BookDRMInformationHandleLCP
import org.nypl.simplified.books.book_database.api.BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB
import org.nypl.simplified.books.book_database.api.BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF
import org.nypl.simplified.files.DirectoryUtilities
import org.nypl.simplified.tests.TestDirectories
import java.io.File

class BookDRMInformationHandleLCPTest {

  private var updates: Int = 0
  private lateinit var directory1: File
  private lateinit var directory0: File

  @BeforeEach
  fun testSetup() {
    this.directory0 = TestDirectories.temporaryDirectory()
    this.directory1 = TestDirectories.temporaryDirectory()
    this.updates = 0
  }

  @AfterEach
  fun testTearDown() {
    DirectoryUtilities.directoryDelete(this.directory0)
    DirectoryUtilities.directoryDelete(this.directory1)
  }

  private fun countUpdateCalls() {
    this.updates += 1
  }

  /**
   * Creating a handle from an empty directory yields an empty handle.
   *
   * @throws Exception On errors
   */

  @Test
  fun testEmptyEPUB() {
    val handle =
      BookDRMInformationHandleLCP(
        directory = this.directory0,
        format = BOOK_FORMAT_EPUB,
        onUpdate = this::countUpdateCalls
      )
    assertEquals("LCP", File(this.directory0, "epub-drm.txt").readText())
  }

  /**
   * Creating a handle from an empty directory yields an empty handle.
   *
   * @throws Exception On errors
   */

  @Test
  fun testEmptyPDF() {
    val handle =
      BookDRMInformationHandleLCP(
        directory = this.directory0,
        format = BOOK_FORMAT_PDF,
        onUpdate = this::countUpdateCalls
      )
    assertEquals("LCP", File(this.directory0, "pdf-drm.txt").readText())
  }

  /**
   * Creating a handle from an empty directory yields an empty handle.
   *
   * @throws Exception On errors
   */

  @Test
  fun testSetPassphrase() {
    val handle0 =
      BookDRMInformationHandleLCP(
        directory = this.directory0,
        format = BOOK_FORMAT_EPUB,
        onUpdate = this::countUpdateCalls
      )

    handle0.setHashedPassphrase("VGhlIFNpeHRlZW4gTWVuIE9mIFRhaW4K")
    assertEquals("VGhlIFNpeHRlZW4gTWVuIE9mIFRhaW4K", handle0.info.hashedPassphrase)
    assertEquals(1, this.updates)

    val handle1 =
      BookDRMInformationHandleLCP(
        directory = this.directory0,
        format = BOOK_FORMAT_EPUB,
        onUpdate = this::countUpdateCalls
      )

    assertEquals("VGhlIFNpeHRlZW4gTWVuIE9mIFRhaW4K", handle1.info.hashedPassphrase)
    assertEquals(1, this.updates)
  }
}
