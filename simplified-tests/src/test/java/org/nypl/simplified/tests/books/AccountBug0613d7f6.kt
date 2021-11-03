package org.nypl.simplified.tests.books

import android.content.Context
import io.reactivex.subjects.PublishSubject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.nypl.simplified.accounts.database.AccountAuthenticationCredentialsStore
import org.nypl.simplified.accounts.database.AccountsDatabases
import org.nypl.simplified.books.book_database.BookDatabases
import org.nypl.simplified.books.formats.BookFormatSupport
import org.nypl.simplified.books.formats.BookFormatSupportParameters
import org.nypl.simplified.tests.TestDirectories
import org.nypl.simplified.tests.mocking.MockAccountProviders
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class AccountBug0613d7f6 {

  private lateinit var accountDirectory: File
  private lateinit var accountsDirectory: File
  private lateinit var accountsDirectoryGraveyard: File
  private lateinit var authStore: File
  private lateinit var authStoreTmp: File
  private lateinit var directory: File
  private lateinit var tmpDirectory: File
  private lateinit var zip: File

  @BeforeEach
  fun setup() {
    this.directory =
      TestDirectories.temporaryDirectory()

    this.tmpDirectory =
      File(this.directory, "tmp")
    this.accountsDirectory =
      File(this.directory, "accounts")
    this.accountsDirectoryGraveyard =
      File(this.directory, "accounts-graveyard")
    this.accountDirectory =
      File(this.accountsDirectory, "df2e029d-47ab-4dae-9142-51408ac12411")

    this.authStore =
      File(this.directory, "auth.txt")
    this.authStoreTmp =
      File(this.directory, "auth.txt.tmp")

    this.accountDirectory.mkdirs()
    this.accountsDirectory.mkdirs()
    this.tmpDirectory.mkdirs()

    this.zip = File(this.tmpDirectory, "broken-epub.zip")

    AccountBug0613d7f6::class.java.getResourceAsStream("/org/nypl/simplified/tests/books/broken-account.zip")!!.use { input ->
      FileOutputStream(this.zip).use { output ->
        input.copyTo(output)
        output.flush()
      }
    }

    unpackZip(this.tmpDirectory, "broken-epub.zip")

    File(this.tmpDirectory, "account.json")
      .renameTo(File(this.accountDirectory, "account.json"))
    File(this.tmpDirectory, "books")
      .renameTo(File(this.accountDirectory, "books"))
  }

  /**
   * See https://www.notion.so/lyrasis/Android-app-crashes-on-launch-and-removes-LYRASIS-Reads-library-0613d7f6e08241ea8048dbc2041fe477
   *
   * The app was failing to read a bookmark, and then destroying the account as a response.
   * This test checks that that no longer happens.
   */

  @Test
  fun testBrokenAccount() {
    val context =
      Mockito.mock(Context::class.java)

    AccountsDatabases.openDatabase(
      AccountAuthenticationCredentialsStore.open(authStore, authStoreTmp),
      PublishSubject.create(),
      MockAccountProviders.fakeAccountProviders(),
      BookDatabases,
      BookFormatSupport.create(
        BookFormatSupportParameters(
          supportsPDF = false,
          supportsAdobeDRM = false,
          supportsAxisNow = false,
          supportsAudioBooks = null,
          supportsLCP = false
        )
      ),
      context,
      this.accountsDirectory,
      this.accountsDirectoryGraveyard
    )
  }

  private fun unpackZip(
    path: File,
    zipName: String
  ) {
    FileInputStream(File(path, zipName)).use { inputStream ->
      ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
        val buffer = ByteArray(1024)
        var count: Int

        while (true) {
          val entry = zipInputStream.nextEntry ?: break
          val filename = entry.name
          val outputFile = File(path, filename)

          if (entry.isDirectory) {
            outputFile.mkdirs()
            continue
          }

          FileOutputStream(outputFile).use { fileOutputStream ->
            while (zipInputStream.read(buffer).also { count = it } != -1) {
              fileOutputStream.write(buffer, 0, count)
            }
            fileOutputStream.close()
          }
          zipInputStream.closeEntry()
        }
      }
    }
  }
}
