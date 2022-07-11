package org.nypl.simplified.tests.books.bookmarks

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.slf4j.LoggerFactory
import java.util.LinkedList
import java.util.Queue

class EndpointDispatcher : Dispatcher() {

  private val logger =
    LoggerFactory.getLogger(EndpointDispatcher::class.java)

  val responsesByEndpoint =
    mutableMapOf<String, Queue<MockResponse>>()

  fun addResponse(endpoint: String, response: MockResponse) {
    val queue = this.responsesByEndpoint[endpoint] ?: LinkedList()
    queue.add(response)

    logger.debug("added response for endpoint {}", endpoint)
    this.responsesByEndpoint[endpoint] = queue
  }

  override fun dispatch(request: RecordedRequest): MockResponse {
    val endpoint = request.requestUrl?.encodedPath!!
    return this.responsesByEndpoint[endpoint]!!.poll()!!
  }
}
