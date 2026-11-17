package org.nypl.simplified.tests.mdm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.thepalaceproject.mdm.MDMConfigurationParser
import java.net.URI

class MDMConfigurationParserTest {

  @Test
  fun fieldNameFormat() {
    assertEquals("Library-0", MDMConfigurationParser.fieldName(0))
    assertEquals("Library-99", MDMConfigurationParser.fieldName(99))
  }

  @Test
  fun emptyConfiguration() {
    val result =
      MDMConfigurationParser.parse(emptyMap())

    assertTrue(result.configuration.libraryURIs.isEmpty())
    assertTrue(result.invalidFields.isEmpty())
  }

  @Test
  fun sparseFieldsTakenInIndexOrder() {
    val result =
      MDMConfigurationParser.parse(
        mapOf(
          "Library-0" to "urn:uuid:00000000-0000-4000-8000-000000000000",
          "Library-2" to "urn:uuid:00000000-0000-4000-8000-000000000002",
          "Library-5" to "urn:uuid:00000000-0000-4000-8000-000000000005",
        ),
      )

    assertEquals(
      listOf(
        URI.create("urn:uuid:00000000-0000-4000-8000-000000000000"),
        URI.create("urn:uuid:00000000-0000-4000-8000-000000000002"),
        URI.create("urn:uuid:00000000-0000-4000-8000-000000000005"),
      ),
      result.configuration.libraryURIs,
    )
    assertTrue(result.invalidFields.isEmpty())
  }

  @Test
  fun blankFieldsSkipped() {
    val result =
      MDMConfigurationParser.parse(
        mapOf(
          "Library-0" to "   ",
          "Library-1" to "urn:uuid:00000000-0000-4000-8000-000000000001",
        ),
      )

    assertEquals(
      listOf(
        URI.create("urn:uuid:00000000-0000-4000-8000-000000000001"),
      ),
      result.configuration.libraryURIs,
    )
    assertTrue(result.invalidFields.isEmpty())
  }

  @Test
  fun invalidValuesReported() {
    val result =
      MDMConfigurationParser.parse(
        mapOf(
          "Library-0" to "not-a-uuid",
          "Library-1" to "urn:isbn:1234",
          "Library-2" to "urn:uuid:00000000-0000-4000-8000",
          "Library-3" to "urn:uuid:00000000-0000-4000-8000-00000000000g",
        ),
      )

    assertTrue(result.configuration.libraryURIs.isEmpty())
    assertEquals(
      listOf("Library-0", "Library-1", "Library-2", "Library-3"),
      result.invalidFields.map { it.field },
    )
    assertEquals("not-a-uuid", result.invalidFields[0].value)
  }

  @Test
  fun valuesNormalizedToLowercase() {
    val result =
      MDMConfigurationParser.parse(
        mapOf(
          "Library-0" to "urn:uuid:00000000-0000-4000-8000-00000000000A",
        ),
      )

    assertEquals(
      listOf(
        URI.create("urn:uuid:00000000-0000-4000-8000-00000000000a"),
      ),
      result.configuration.libraryURIs,
    )
  }

  @Test
  fun unknownKeysIgnored() {
    val result =
      MDMConfigurationParser.parse(
        mapOf(
          "Foo" to "urn:uuid:00000000-0000-4000-8000-000000000000",
          "Library" to "urn:uuid:00000000-0000-4000-8000-000000000001",
        ),
      )

    assertTrue(result.configuration.libraryURIs.isEmpty())
    assertTrue(result.invalidFields.isEmpty())
  }

  @Test
  fun allFields() {
    val values =
      (0 until MDMConfigurationParser.FIELD_COUNT)
        .associate { "Library-$it" to "urn:uuid:00000000-0000-4000-8000-0000000000%02x".format(it) }

    val result =
      MDMConfigurationParser.parse(values)

    assertEquals(MDMConfigurationParser.FIELD_COUNT, result.configuration.libraryURIs.size)
    assertTrue(result.invalidFields.isEmpty())
  }
}
