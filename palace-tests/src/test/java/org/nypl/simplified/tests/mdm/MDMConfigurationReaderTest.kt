package org.nypl.simplified.tests.mdm

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.thepalaceproject.mdm.MDMConfiguration
import org.thepalaceproject.mdm.MDMConfigurationReader
import java.net.URI

/**
 * Tests for [MDMConfigurationReader]. The [Context], [RestrictionsManager],
 * and [Bundle] collaborators are mocked, so no Android runtime is required.
 */
class MDMConfigurationReaderTest {

  private val libraryA =
    URI.create("urn:uuid:00000000-0000-4000-8000-000000000000")
  private val libraryB =
    URI.create("urn:uuid:00000000-0000-4000-8000-000000000001")

  private val context =
    Mockito.mock(Context::class.java)
  private val manager =
    Mockito.mock(RestrictionsManager::class.java)
  private val bundle =
    Mockito.mock(Bundle::class.java)

  @BeforeEach
  fun before() {
    Mockito.`when`(this.context.getSystemService(RestrictionsManager::class.java))
      .thenReturn(this.manager)
  }

  /**
   * On a device that is not managed, the [RestrictionsManager] reports no
   * application restrictions. Reading the configuration then yields an empty
   * configuration with no invalid fields.
   */

  @Test
  fun unmanagedDeviceYieldsEmptyConfiguration() {
    // `manager.applicationRestrictions` is left unstubbed: an unstubbed mock
    // returns null, as the real manager does when the device is not managed.

    val data =
      MDMConfigurationReader(this.context).readConfiguration()

    assertTrue(data.rawValues.isEmpty())
    assertTrue(data.parseResult.configuration.libraryURIs.isEmpty())
    assertTrue(data.parseResult.invalidFields.isEmpty())
  }

  /**
   * Configured libraries are parsed in field-index order; absent fields are
   * skipped.
   */

  @Test
  fun managedDeviceParsesConfiguredLibraries() {
    this.mockBundle(
      mapOf(
        "Library-0" to "urn:uuid:00000000-0000-4000-8000-000000000000",
        "Library-2" to "urn:uuid:00000000-0000-4000-8000-000000000001",
      )
    )

    val data =
      MDMConfigurationReader(this.context).readConfiguration()

    assertEquals(
      mapOf(
        "Library-0" to "urn:uuid:00000000-0000-4000-8000-000000000000",
        "Library-2" to "urn:uuid:00000000-0000-4000-8000-000000000001",
      ),
      data.rawValues,
    )
    assertEquals(MDMConfiguration(listOf(this.libraryA, this.libraryB)), data.parseResult.configuration)
    assertTrue(data.parseResult.invalidFields.isEmpty())
  }

  /**
   * A present but invalid value is reported as an invalid field, and does not
   * prevent the other configured libraries from being parsed.
   */

  @Test
  fun managedDeviceReportsInvalidFields() {
    this.mockBundle(
      mapOf(
        "Library-0" to "urn:uuid:00000000-0000-4000-8000-000000000000",
        "Library-1" to "https://not-a-library.example.com",
      )
    )

    val data =
      MDMConfigurationReader(this.context).readConfiguration()

    assertEquals(MDMConfiguration(listOf(this.libraryA)), data.parseResult.configuration)
    assertEquals(1, data.parseResult.invalidFields.size)
    assertEquals("Library-1", data.parseResult.invalidFields[0].field)
    assertEquals("https://not-a-library.example.com", data.parseResult.invalidFields[0].value)
  }

  /**
   * Blank values are skipped, even when they occupy a lower field index than
   * a configured library.
   */

  @Test
  fun managedDeviceSkipsBlankValues() {
    this.mockBundle(
      mapOf(
        "Library-0" to "urn:uuid:00000000-0000-4000-8000-000000000000",
        "Library-1" to "   ",
        "Library-2" to "urn:uuid:00000000-0000-4000-8000-000000000001",
      )
    )

    val data =
      MDMConfigurationReader(this.context).readConfiguration()

    assertEquals(MDMConfiguration(listOf(this.libraryA, this.libraryB)), data.parseResult.configuration)
    assertTrue(data.parseResult.invalidFields.isEmpty())
  }

  /**
   * Values in keys that are not managed configuration fields are ignored.
   */

  @Test
  fun managedDeviceIgnoresNonLibraryKeys() {
    this.mockBundle(
      mapOf(
        "some-other-policy" to "urn:uuid:00000000-0000-4000-8000-000000000000",
      )
    )

    val data =
      MDMConfigurationReader(this.context).readConfiguration()

    assertTrue(data.parseResult.configuration.libraryURIs.isEmpty())
    assertTrue(data.parseResult.invalidFields.isEmpty())
  }

  /**
   * A key whose value is not a string is skipped rather than reported as
   * invalid, as the real [Bundle.getString] returns `null` for such values.
   */

  @Test
  fun managedDeviceSkipsKeysWithNoStringValue() {
    Mockito.`when`(this.manager.applicationRestrictions).thenReturn(this.bundle)
    Mockito.`when`(this.bundle.keySet()).thenReturn(setOf("Library-0"))
    // `bundle.getString("Library-0")` is left unstubbed: it returns null,
    // matching the real Bundle for values that are not strings.

    val data =
      MDMConfigurationReader(this.context).readConfiguration()

    assertTrue(data.rawValues.isEmpty())
    assertTrue(data.parseResult.configuration.libraryURIs.isEmpty())
    assertTrue(data.parseResult.invalidFields.isEmpty())
  }

  private fun mockBundle(values: Map<String, String>) {
    Mockito.`when`(this.manager.applicationRestrictions).thenReturn(this.bundle)
    Mockito.`when`(this.bundle.keySet()).thenReturn(values.keys)
    for ((key, value) in values) {
      Mockito.`when`(this.bundle.getString(key)).thenReturn(value)
    }
  }
}
