package org.nypl.simplified.taskrecorder.api

import org.librarysimplified.http.api.LSHTTPResponseProperties

/**
 * A task recorder. Record the steps of complex tasks to explain how and why errors occurred.
 */

interface TaskRecorderType {
  /**
   * Add an attribute to the task.
   */

  fun addAttribute(
    name: String,
    value: String
  )

  /**
   * Add a set of attributes to the task.
   */

  fun addAttributes(attributes: Map<String, String>)

  /**
   * Add a set of attributes to the task.
   */

  fun addAttributesIfPresent(attributes: Map<String, String>?) {
    this.addAttributes(attributes ?: emptyMap())
  }

  /**
   * Start a new controller task step.
   */

  fun beginNewStep(message: String): TaskStep

  /**
   * Resolve the current step and mark it as having succeeded.
   */

  fun currentStepSucceeded(message: String): TaskStep

  /**
   * Resolve the current step and mark it as having failed.
   */

  fun currentStepFailed(
    message: String,
    errorCode: String,
    exception: Throwable? = null,
    extraMessages: List<String>
  ): TaskStep

  /**
   * If the current step has not failed, fail it with the given error. If the current step
   * has failed but no exception is present, add the given exception. Otherwise, if an exception
   * is present, add a suppressed exception.
   */

  fun currentStepFailedAppending(
    message: String,
    errorCode: String,
    exception: Throwable,
    extraMessages: List<String>
  ): TaskStep

  /**
   * Complete recording of all steps.
   */

  fun <A> finishSuccess(result: A): TaskResult.Success<A>

  /**
   * Complete recording of all steps.
   */

  fun <A> finishFailure(): TaskResult.Failure<A>

  /**
   * @return The current step
   */

  fun currentStep(): TaskStep?

  /**
   * Add a series of steps.
   */

  fun addAll(steps: List<TaskStep>)

  /**
   * Publish the given HTTP response properties as attributes.
   */

  fun addPropertiesAsAttributes(properties: LSHTTPResponseProperties) {
    this.addAttribute("HTTPStatusCode", properties.status.toString())
    this.addAttribute("HTTPMessage", properties.message)
    this.addAttribute("HTTPContentLength", (properties.contentLength ?: -1).toString())
    this.addAttribute("HTTPContentType", properties.contentType.toString())

    val problem = properties.problemReport
    if (problem != null) {
      this.addAttribute("ProblemReport (Title)", problem.title ?: "null")
      this.addAttribute("ProblemReport (Type)", problem.type ?: "null")
      this.addAttribute("ProblemReport (Detail)", problem.detail ?: "null")
      this.addAttribute("ProblemReport (Status)", problem.status?.toString() ?: "null")
    }
  }
}
