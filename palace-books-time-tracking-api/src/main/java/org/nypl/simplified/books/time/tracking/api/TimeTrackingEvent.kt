package org.nypl.simplified.books.time.tracking.api

import java.time.OffsetDateTime

/**
 * An event in the time tracking event log.
 *
 * The event log is an append-only audit trail of the operations performed by the time tracking
 * pipeline. Each kind of event is a distinct class carrying exactly the fields that apply to it,
 * so that a value of a given class can never carry fields that do not apply to it.
 */
sealed interface TimeTrackingEvent {
  /**
   * The time at which the event was recorded
   */
  val time: OffsetDateTime

  /**
   * The identifier of the account provider
   */
  val libraryID: String

  /**
   * The identifier of the book
   */
  val bookID: String

  /**
   * The name of the operation recorded by the event, which is the name of the class of the
   * event. The name is the value that is persisted in the database, and the database
   * constrains it to the set of names of the event classes.
   */
  val operationName: String
}
