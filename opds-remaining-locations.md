# Remaining OPDSAcquisitionFeedEntry References (Legitimate Boundaries)

As of commit `dc6d5326c` on branch `feature/opds2-catalog-2`.

## Serialization Boundaries
- `palace-books-api/src/main/java/org/nypl/simplified/books/api/BookIDs.kt` — `newFromOPDSEntry(OPDSAcquisitionFeedEntry)` for ID derivation
- `palace-books-database-api/src/main/java/org/nypl/simplified/books/book_database/api/BookDatabaseEntryType.kt` — `writeOPDSEntry(OPDSAcquisitionFeedEntry)` interface
- `palace-books-database/src/main/java/org/nypl/simplified/books/book_database/BookDatabaseEntry.kt` — implements `writeOPDSEntry`
- `palace-books-database-api/src/main/java/org/nypl/simplified/books/book_database/api/BookFormats.kt` — `inferFormat(OPDSAcquisitionFeedEntry)`

## Internal Borrowing Mechanism (wire protocol)
- `palace-books-borrowing/src/main/java/org/nypl/simplified/books/borrowing/BorrowContextType.kt` — `OPDSAcquisitionPathElement`, tracks borrow path
- `palace-books-borrowing/src/main/java/org/nypl/simplified/books/borrowing/BorrowTask.kt` — uses OPDS types for borrowing flow
- `palace-books-borrowing/src/main/java/org/nypl/simplified/books/borrowing/internal/BorrowLoanCreate.kt` — parses OPDS from HTTP responses

## Preview System (candidate for follow-up migration)
- `palace-books-preview/src/main/java/org/nypl/simplified/books/preview/BookPreviewParameters.kt` — `feedEntry: OPDSAcquisitionFeedEntry`
- `palace-books-preview/src/main/java/org/nypl/simplified/books/preview/BookPreviewTask.kt` — uses the parameter
- `palace-books-preview/src/main/java/org/nypl/simplified/books/preview/BookPreviewAcquisitions.kt` — `pickBestPreviewAcquisition(entry: OPDSAcquisitionFeedEntry)`
- `palace-books-controller/src/main/java/org/nypl/simplified/books/controller/Controller.kt` — converts at boundary: `entry.toOPDSAcquisitionFeedEntry()` when creating BookPreviewTask

## Feed Loading (internal)
- `palace-feeds/src/main/java/org/nypl/simplified/feeds/FeedLoader.kt` — parses OPDS 1.2 feeds internally
- `palace-ui/src/main/java/org/nypl/simplified/ui/main/MainServices.kt` — uses `OPDSAcquisitionFeedEntryParser`

## UI Debug Utility (candidate for follow-up)
- `palace-ui/src/main/java/org/nypl/simplified/ui/catalog/CatalogCoverDebugLabels.kt` — `drmNameOf(entry: OPDSAcquisitionFeedEntry)`

## Controller (serialization boundary)
- `palace-books-controller/src/main/java/org/nypl/simplified/books/controller/ProfileAccountLogoutTask.kt` — `entry.writeOPDSEntry(newFeedEntry.toOPDSAcquisitionFeedEntry())`
