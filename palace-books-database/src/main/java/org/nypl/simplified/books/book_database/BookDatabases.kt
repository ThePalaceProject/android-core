package org.nypl.simplified.books.book_database

import android.app.Application
import org.librarysimplified.http.api.LSHTTPClientType
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.books.book_database.api.BookDatabaseException
import org.nypl.simplified.books.book_database.api.BookDatabaseFactoryType
import org.nypl.simplified.books.book_database.api.BookDatabaseType
import org.nypl.simplified.books.formats.api.BookFormatSupportType
import org.nypl.simplified.feeds.api.FeedEntryParserType
import org.nypl.simplified.feeds.api.FeedEntrySerializerType
import org.nypl.simplified.feeds.opds12.FeedEntryParser
import org.nypl.simplified.feeds.opds12.FeedEntrySerializer
import org.nypl.simplified.opds.core.OPDSJSONParser
import org.nypl.simplified.opds.core.OPDSJSONSerializer
import java.io.File

object BookDatabases : BookDatabaseFactoryType {
  @Throws(BookDatabaseException::class)
  override fun openDatabase(
    context: Application,
    parser: FeedEntryParserType,
    serializer: FeedEntrySerializerType,
    httpClient: LSHTTPClientType,
    formats: BookFormatSupportType,
    owner: AccountID,
    directory: File,
  ): BookDatabaseType =
    BookDatabase.open(
      context = context,
      directory = directory,
      formats = formats,
      httpClient = httpClient,
      owner = owner,
      parser = parser,
      serializer = serializer,
    )

  @Throws(BookDatabaseException::class)
  override fun openDatabase(
    context: Application,
    formats: BookFormatSupportType,
    httpClient: LSHTTPClientType,
    owner: AccountID,
    directory: File,
  ): BookDatabaseType =
    BookDatabase.open(
      context = context,
      directory = directory,
      formats = formats,
      httpClient = httpClient,
      owner = owner,
      parser = FeedEntryParser(OPDSJSONParser.newParser()),
      serializer = FeedEntrySerializer(OPDSJSONSerializer.newSerializer()),
    )
}
