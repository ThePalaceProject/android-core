package org.nypl.simplified.ui.settings

import com.google.common.util.concurrent.MoreExecutors
import com.io7m.jattribute.core.AttributeType
import org.librarysimplified.services.api.Services
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import org.nypl.simplified.ui.main.MainAttributes
import java.net.URI

object SettingsCustomOPDSModel {

  private val task: AttributeType<List<String>> =
    MainAttributes.attributes.withValue(listOf())

  val taskUI: AttributeType<List<String>> =
    MainAttributes.attributes.withValue(listOf())

  private val taskRunning: AttributeType<Boolean> =
    MainAttributes.attributes.withValue(false)

  val taskRunningUI: AttributeType<Boolean> =
    MainAttributes.attributes.withValue(false)

  init {
    MainAttributes.wrapAttribute(
      this.taskRunning,
      this.taskRunningUI
    )
    MainAttributes.wrapAttribute(
      this.task,
      this.taskUI
    )
  }

  fun createCustomOPDSFeed(
    uri: String
  ) {
    this.taskRunning.set(true)

    val future =
      Services.serviceDirectory()
        .requireService(ProfilesControllerType::class.java)
        .profileAccountCreateCustomOPDS(URI(uri))

    future.addListener(
      {
        this.taskRunning.set(false)
        when (val r = future.get()) {
          is TaskResult.Failure -> {
            val messages = mutableListOf("Failed")
            for (step in r.steps) {
              messages.add("${step.description}: ${step.resolution}: ${step.message}")
            }
            this.task.set(messages.toList())
          }

          is TaskResult.Success -> {
            this.task.set(listOf("Succeeded."))
          }
        }
      },
      MoreExecutors.directExecutor()
    )
  }
}
