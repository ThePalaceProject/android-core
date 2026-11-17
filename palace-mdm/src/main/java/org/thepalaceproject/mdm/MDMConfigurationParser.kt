package org.thepalaceproject.mdm

import java.net.URI

/**
 * A parser for the managed device (MDM) configuration.
 *
 * The configuration consists of the flat string fields `Library-0` through
 * `Library-99`, each holding the registry UUID of a library, formatted as
 * `urn:uuid:...`. Absent and blank fields are skipped; the remaining values
 * are taken in index order.
 */
object MDMConfigurationParser {
  /**
   * The prefix of the managed configuration fields naming configured
   * libraries.
   */
  const val FIELD_PREFIX = "Library-"

  /**
   * The number of library fields in the managed configuration schema.
   */
  const val FIELD_COUNT = 100

  private val FIELD_VALUE_PATTERN =
    Regex("urn:uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

  /**
   * @param index The index of the field, in the range `0` to `FIELD_COUNT - 1`
   * @return The name of the managed configuration field for the given index
   */
  fun fieldName(index: Int): String = "$FIELD_PREFIX$index"

  /**
   * Parse the given managed configuration values.
   *
   * @param values The raw values of the managed configuration, keyed by field name
   * @return The parsed configuration, and the fields whose values were present but invalid
   */
  fun parse(values: Map<String, String>): MDMConfigurationParseResult {
    val uris =
      mutableListOf<URI>()
    val invalidFields =
      mutableListOf<MDMConfigurationInvalidField>()

    for (index in 0 until this.FIELD_COUNT) {
      val key =
        this.fieldName(index)
      val value =
        values[key]?.trim()
      if (value == null || value.isEmpty()) {
        continue
      }
      if (!this.FIELD_VALUE_PATTERN.matches(value)) {
        invalidFields.add(MDMConfigurationInvalidField(key, value))
        continue
      }
      uris.add(URI.create(value.lowercase()))
    }

    return MDMConfigurationParseResult(
      configuration = MDMConfiguration(uris),
      invalidFields = invalidFields,
    )
  }
}
