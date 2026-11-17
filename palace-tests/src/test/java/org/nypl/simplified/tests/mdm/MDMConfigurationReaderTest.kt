package org.nypl.simplified.tests.mdm

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowRestrictionsManager
import org.thepalaceproject.mdm.MDMConfiguration
import org.thepalaceproject.mdm.MDMConfigurationReader
import java.net.URI

/**
 * Tests for [MDMConfigurationReader], which reads the managed device
 * configuration from the system [RestrictionsManager].
 *
 * These tests require a live [RestrictionsManager] service, and are therefore
 * run with [RobolectricTestRunner]. As with the other Robolectric tests in
 * this project, they use JUnit 4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class MDMConfigurationReaderTest {

  private val libraryA =
    URI.create("urn:uuid:00000000-0000-4000-8000-000000000000")
  private val libraryB =
    URI.create("urn:uuid:00000000-0000-4000-8000-000000000001")

  @Before
  fun before() {
    this.setRestrictions(null)
  }

  /**
   * On a device that is not managed, the [RestrictionsManager] reports no
   * application restrictions. Reading the configuration then yields an empty
   * configuration with no invalid fields.
   */

  @Test
  fun unmanagedDeviceYieldsEmptyConfiguration() {
    val result =
      MDMConfigurationReader(this.context()).readConfiguration()

    assertTrue(result.configuration.libraryURIs.isEmpty())
    assertTrue(result.invalidFields.isEmpty())
  }

  /**
   * Configured libraries are parsed in field-index order; absent fields are
   * skipped.
   */

  @Test
  fun managedDeviceParsesConfiguredLibraries() {
    val bundle = Bundle()
    bundle.putString("Library-0", "urn:uuid:00000000-0000-4000-8000-000000000000")
    bundle.putString("Library-2", "urn:uuid:00000000-0000-4000-8000-000000000001")
    this.setRestrictions(bundle)

    val result =
      MDMConfigurationReader(this.context()).readConfiguration()

    assertEquals(MDMConfiguration(listOf(this.libraryA, this.libraryB)), result.configuration)
    assertTrue(result.invalidFields.isEmpty())
  }

  /**
   * A present but invalid value is reported as an invalid field, and does not
   * prevent the other configured libraries from being parsed.
   */

  @Test
  fun managedDeviceReportsInvalidFields() {
    val bundle = Bundle()
    bundle.putString("Library-0", "urn:uuid:00000000-0000-4000-8000-000000000000")
    bundle.putString("Library-1", "https://not-a-library.example.com")
    this.setRestrictions(bundle)

    val result =
      MDMConfigurationReader(this.context()).readConfiguration()

    assertEquals(MDMConfiguration(listOf(this.libraryA)), result.configuration)
    assertEquals(1, result.invalidFields.size)
    assertEquals("Library-1", result.invalidFields[0].field)
    assertEquals("https://not-a-library.example.com", result.invalidFields[0].value)
  }

  /**
   * Blank values are skipped, even when they occupy a lower field index than
   * a configured library.
   */

  @Test
  fun managedDeviceSkipsBlankValues() {
    val bundle = Bundle()
    bundle.putString("Library-0", "urn:uuid:00000000-0000-4000-8000-000000000000")
    bundle.putString("Library-1", "   ")
    bundle.putString("Library-2", "urn:uuid:00000000-0000-4000-8000-000000000001")
    this.setRestrictions(bundle)

    val result =
      MDMConfigurationReader(this.context()).readConfiguration()

    assertEquals(MDMConfiguration(listOf(this.libraryA, this.libraryB)), result.configuration)
    assertTrue(result.invalidFields.isEmpty())
  }

  /**
   * Values in keys that are not managed configuration fields are ignored.
   */

  @Test
  fun managedDeviceIgnoresNonLibraryKeys() {
    val bundle = Bundle()
    bundle.putString("some-other-policy", "urn:uuid:00000000-0000-4000-8000-000000000000")
    this.setRestrictions(bundle)

    val result =
      MDMConfigurationReader(this.context()).readConfiguration()

    assertTrue(result.configuration.libraryURIs.isEmpty())
    assertTrue(result.invalidFields.isEmpty())
  }

  /**
   * A value that is not a plain string is ignored rather than reported as
   * invalid.
   */

  @Test
  fun managedDeviceIgnoresNonStringValues() {
    val bundle = Bundle()
    bundle.putStringArray("Library-0", arrayOf("urn:uuid:00000000-0000-4000-8000-000000000000"))
    this.setRestrictions(bundle)

    val result =
      MDMConfigurationReader(this.context()).readConfiguration()

    assertTrue(result.configuration.libraryURIs.isEmpty())
    assertTrue(result.invalidFields.isEmpty())
  }

  private fun context(): Context =
    RuntimeEnvironment.getApplication()

  private fun setRestrictions(bundle: Bundle?) {
    val manager =
      this.context().getSystemService(RestrictionsManager::class.java)
    val shadow: ShadowRestrictionsManager =
      Shadow.extract(manager)
    shadow.setApplicationRestrictions(bundle)
  }
}
