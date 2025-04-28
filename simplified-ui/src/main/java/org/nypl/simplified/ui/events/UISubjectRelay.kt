package org.nypl.simplified.ui.events

import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import org.nypl.simplified.threads.UIThread

/**
 * A trivial relay that exposes an observable that is guaranteed to be observed on the UI thread.
 */

class UISubjectRelay<E> private constructor(
  private val subject: Subject<E>,
  private val subscription: Disposable
) : Disposable, AutoCloseable {

  companion object {
    fun <E> create(
      source: Observable<E>
    ): UISubjectRelay<E> {
      val baseSubject =
        PublishSubject.create<E>()
          .toSerialized()
      val subscription =
        source.subscribe { event ->
          if (event != null) {
            UIThread.runOnUIThread {
              baseSubject.onNext(event)
            }
          }
        }
      return UISubjectRelay(baseSubject, subscription)
    }
  }

  val events: Observable<E> =
    this.subject

  override fun dispose() {
    return this.subscription.dispose()
  }

  override fun isDisposed(): Boolean {
    return this.subscription.isDisposed
  }

  override fun close() {
    this.dispose()
  }
}
