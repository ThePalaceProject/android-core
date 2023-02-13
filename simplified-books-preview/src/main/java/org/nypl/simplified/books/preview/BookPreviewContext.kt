package org.nypl.simplified.books.preview

import org.nypl.simplified.taskrecorder.api.TaskResult

interface BookPreviewContext {

  fun execute(): TaskResult<*>
}
