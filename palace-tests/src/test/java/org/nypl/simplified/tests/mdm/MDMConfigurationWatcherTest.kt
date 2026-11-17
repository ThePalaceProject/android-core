package org.nypl.simplified.tests.mdm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.thepalaceproject.mdm.MDMConfiguration
import org.thepalaceproject.mdm.MDMConfigurationParseResult
import org.thepalaceproject.mdm.MDMConfigurationReader
import org.thepalaceproject.mdm.MDMLibraryApplier
import org.thepalaceproject.mdm.MDMConfigurationWatcher
import java.net.URI

/**
 * Tests for [MDMConfigurationWatcher]. The [Context], reader, and applier
 * collaborators are mocked, so no Android runtime is required.
 */
class MDMConfigurationWatcherTest {

  private val libraryA =
    URI.create("urn:uuid:00000000-0000-4000-8000-000000000000")
  private val libraryB =
    URI.create("urn:uuid:00000000-0000-4000-8000-000000000001")

  private val emptyConfiguration =
    MDMConfigurationParseResult(
      configuration = MDMConfiguration(emptyList()),
      invalidFields = emptyList(),
    )
  private val configurationA =
    MDMConfigurationParseResult(
      configuration = MDMConfiguration(listOf(this.libraryA)),
      invalidFields = emptyList(),
    )
  private val configurationB =
    MDMConfigurationParseResult(
      configuration = MDMConfiguration(listOf(this.libraryA, this.libraryB)),
      invalidFields = emptyList(),
    )

  private val context =
    Mockito.mock(Context::class.java)
  private val reader =
    Mockito.mock(MDMConfigurationReader::class.java)
  private val applier =
    Mockito.mock(MDMLibraryApplier::class.java)
  private val watcher =
    MDMConfigurationWatcher(this.context, this.reader, this.applier)

  /**
   * Starting the watcher applies the current configuration.
   */

  @Test
  fun startAppliesCurrentConfiguration() {
    Mockito.`when`(this.reader.readConfiguration())
      .thenReturn(this.configurationA)

    this.startWatcher()

    Mockito.verify(this.applier, Mockito.times(1))
      .apply(this.configurationA)
  }

  /**
   * A change notification that leaves the configuration unchanged does not
   * re-apply it.
   */

  @Test
  fun unchangedConfigurationIsNotReapplied() {
    Mockito.`when`(this.reader.readConfiguration())
      .thenReturn(this.configurationA)

    val receiver =
      this.startWatcher()
    receiver.onReceive(this.context, Intent())

    Mockito.verify(this.applier, Mockito.times(1))
      .apply(this.configurationA)
  }

  /**
   * A change notification that alters the configuration re-applies it.
   */

  @Test
  fun changedConfigurationIsReapplied() {
    Mockito.`when`(this.reader.readConfiguration())
      .thenReturn(this.configurationA)
      .thenReturn(this.configurationB)

    val receiver =
      this.startWatcher()
    receiver.onReceive(this.context, Intent())

    Mockito.verify(this.applier).apply(this.configurationA)
    Mockito.verify(this.applier).apply(this.configurationB)
  }

  /**
   * Stopping the watcher unregisters the broadcast receiver.
   */

  @Test
  fun stopUnregistersReceiver() {
    Mockito.`when`(this.reader.readConfiguration())
      .thenReturn(this.emptyConfiguration)

    val receiver =
      this.startWatcher()
    this.watcher.stop()

    Mockito.verify(this.context).unregisterReceiver(receiver)
  }

  /**
   * Stopping the watcher before it has been started is a no-op.
   */

  @Test
  fun stopWithoutStartIsANoOp() {
    this.watcher.stop()

    Mockito.verifyNoInteractions(this.context)
  }

  private fun startWatcher(): BroadcastReceiver {
    this.watcher.start()
    val captor =
      ArgumentCaptor.forClass(BroadcastReceiver::class.java)
    Mockito.verify(this.context).registerReceiver(
      captor.capture(),
      Mockito.any(IntentFilter::class.java),
      Mockito.eq(Context.RECEIVER_NOT_EXPORTED),
    )
    return captor.value
  }
}
