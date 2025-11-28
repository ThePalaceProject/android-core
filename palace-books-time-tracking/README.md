org.librarysimplified.books.time.tracking
===

The `org.librarysimplified.books.time.tracking` module provides all the code necessary to track
the time a book is being read or listened to.

## Implementation

The time tracking service is split into four components in order to make each step independently
testable and auditable.

1. The audiobook player publishes time spans. A time span is started when the play button is
   pressed in an audiobook, and ends after one minute or the pause button is pressed, whichever
   comes first. If the pause button _wasn't_ pressed, a new time span is started. This effectively
   means that the audiobook player publishes a stream of time spans as long as a book is playing.
   Once published, the audiobook player doesn't care what happens to the spans.

2. The _collector_ service subscribes to the audiobook player span stream and serializes each
   span to disk in a single directory. Spans are written atomically by creating temporary files 
   and then atomically renaming the temporary files to a naming pattern recognized by the _merger_.

3. The _merger_ service watches the directory written by the _collector_ and, roughly every
   thirty seconds, reads every serialized time span that is older than ninety seconds. The
   reason for having the age cutoff is that we want to be absolutely certain that no new spans
   will arrive for a given minute before we merge them, so we have to be absolutely sure that
   the "most recent minute" is over. The read spans are merged into time tracking entries that
   the server expects and atomically written to an output directory.

4. The _sender_ service watches the directory written by the _merger_ and reads time tracking
   entries from the directory. It sends each entry to the server (batching the entries to
   minimize the number of HTTP calls). For every entry the server claims to have accepted, the
   corresponding entry file is deleted from the _merger_ output directory. Entries that were
   not accepted are retried indefinitely.

All operations are recorded into an append-only audit log that can be captured from the device
when sending error logs.

Errors are logged to Crashlytics with the following attributes:

|Name|Value|
|----|-----|
|`System`|`TimeTracking`|
|`SubSystem`|One of `Collector`, `Merger`, `Sender`|
|`TimeLoss`|Either `true` or `false` depending on whether the system thinks it lost a time tracking entry|
