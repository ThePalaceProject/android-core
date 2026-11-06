# Remaining OPDS References (Post-Migration)

## Status: All public API surfaces migrated to Feed* types

## Remaining `OPDSAcquisitionFeedEntry` references (all at legitimate boundaries):

### Wire Protocol Parsing (HTTP → OPDS XML)
- `palace-books-borrowing/internal/BorrowLoanCreate.kt` - `parseOPDSFeedEntry()` returns `OPDSAcquisitionFeedEntry` from raw XML; immediately converted to `FeedEntryBook`
- `palace-ui/main/MainServices.kt` - creates `OPDSAcquisitionFeedEntryParser` instance for the feed parser

### Serialization (FeedEntryBook → meta.json)
- `palace-books-database/BookDatabase.kt` - `entry.toOPDSAcquisitionFeedEntry()` for JSON serialization
- `palace-books-database/BookDatabaseEntry.kt` - `entry.toOPDSAcquisitionFeedEntry()` for JSON serialization

### Internal Adapter Layer
- `palace-feeds/FeedLoader.kt` - `isEntrySupported(entry: OPDSAcquisitionFeedEntry)` private helper in the 1.2 parsing path

## Other OPDS imports at wire protocol boundaries:
- `BorrowLCPSupport.kt` - `OPDSFeedParserType` for parsing loans feed
- `BorrowLoanCreate.kt` - `OPDSParseException` for error handling
- `ProfileAccountCreateCustomOPDSTask.kt` - parsing custom OPDS feeds
- `BookSyncTask.kt` - `OPDSFeedParserType`/`OPDSParseException`
- `Controller.kt` - `OPDSFeedParserType` field
- `BookDatabase.kt`/`BookDatabases.kt`/`BookDatabaseFactoryType.kt` - JSON parser/serializer types
- `MainServices.kt` - `OPDSFeedParser`/`OPDSSearchParser` instances

## What was eliminated:
- `Book.entry` was `OPDSAcquisitionFeedEntry` → now `FeedEntryBook`
- `BookDatabaseEntryType.writeOPDSEntry(OPDSAcquisitionFeedEntry)` → `writeOPDSEntry(FeedEntryBook)`
- `BookFormats.inferFormat(OPDSAcquisitionFeedEntry)` → removed, only `FeedEntryBook` overload
- `BookAcquisitionSelection.preferredAcquisition(List<OPDSAcquisition>)` → `List<FeedAcquisition>`
- `BookIDs.newFromOPDSEntry(OPDSAcquisitionFeedEntry)` → `newFromFeedEntry(FeedEntryBook)`
- `BorrowAcquisitions.pickBestAcquisitionPath` - uses `FeedAcquisitionPaths.linearize(entry.acquisitions)`
- `BorrowContextType` - `FeedAcquisitionPath`/`FeedAcquisitionPathElement`
- `BorrowTask` - all path types migrated
- `BookPreviewParameters`/`BookPreviewTask`/`BookPreviewAcquisitions` - `FeedEntryBook`/`FeedPreviewAcquisition`
- `CatalogCoverDebugLabels` - `FeedEntryBook` directly
- `CatalogBookStatus`/`CatalogBookAccessibilityStrings`/`CatalogCoverBadgeImages`/`CatalogFeedViewDetails2` - `FeedEntryBook` directly
- All viewer modules - `FeedEntryBook`
- `MockBookDatabaseEntry`/`MockBookDatabase` - `FeedEntryBook`
- All test files - converted at boundaries
