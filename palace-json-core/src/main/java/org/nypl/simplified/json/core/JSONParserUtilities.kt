package org.nypl.simplified.json.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import java.math.BigInteger
import java.net.URI
import java.net.URISyntaxException

/**
 * Utility functions for deserializing elements from JSON.
 *
 * The functions take a strict approach: Types are checked upon key retrieval
 * and exceptions are raised if the type is not exactly as expected.
 */
object JSONParserUtilities {
  /**
   * Check that `n` is an object.
   *
   * @param key An optional advisory key to be used in error messages
   * @param n   A node
   * @return `n` as an [ObjectNode]
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun checkObject(
    key: String?,
    n: JsonNode
  ): ObjectNode =
    when (n.nodeType) {
      JsonNodeType.ARRAY,
      JsonNodeType.BINARY,
      JsonNodeType.BOOLEAN,
      JsonNodeType.MISSING,
      JsonNodeType.NULL,
      JsonNodeType.NUMBER,
      JsonNodeType.POJO,
      JsonNodeType.STRING -> {
        val sb = StringBuilder(128)
        if (key != null) {
          sb.append("Expected: A key '")
          sb.append(key)
          sb.append("' with a value of type Object\n")
          sb.append("Got: A value of type ")
          sb.append(n.nodeType)
          sb.append("\n")
        } else {
          sb.append("Expected: A value of type Object\n")
          sb.append("Got: A value of type ")
          sb.append(n.nodeType)
          sb.append("\n")
        }
        throw JSONParseException(sb.toString())
      }

      JsonNodeType.OBJECT -> {
        n as ObjectNode
      }
    }

  /**
   * Check that `n` is an array.
   *
   * @param key An optional advisory key to be used in error messages
   * @param n   A node
   * @return `n` as an [ObjectNode]
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun checkArray(
    key: String?,
    n: JsonNode
  ): ArrayNode =
    when (n.nodeType) {
      JsonNodeType.ARRAY -> {
        n as ArrayNode
      }

      JsonNodeType.BINARY,
      JsonNodeType.BOOLEAN,
      JsonNodeType.MISSING,
      JsonNodeType.NULL,
      JsonNodeType.NUMBER,
      JsonNodeType.POJO,
      JsonNodeType.OBJECT,
      JsonNodeType.STRING -> {
        val sb = StringBuilder(128)
        if (key != null) {
          sb.append("Expected: A key '")
          sb.append(key)
          sb.append("' with a value of type Object\n")
          sb.append("Got: A value of type ")
          sb.append(n.nodeType)
          sb.append("\n")
        } else {
          sb.append("Expected: A value of type Object\n")
          sb.append("Got: A value of type ")
          sb.append(n.nodeType)
          sb.append("\n")
        }
        throw JSONParseException(sb.toString())
      }
    }

  /**
   * Check that `n` is a string.
   *
   * @param n A node
   * @return `n` as a String
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun checkString(n: JsonNode): String =
    when (n.nodeType) {
      JsonNodeType.STRING -> {
        n.asText()
      }

      JsonNodeType.ARRAY,
      JsonNodeType.BINARY,
      JsonNodeType.BOOLEAN,
      JsonNodeType.MISSING,
      JsonNodeType.NULL,
      JsonNodeType.NUMBER,
      JsonNodeType.POJO,
      JsonNodeType.OBJECT -> {
        val sb = StringBuilder(128)
        sb.append("Expected: A value of type String\n")
        sb.append("Got: A value of type ")
        sb.append(n.nodeType)
        sb.append("\n")
        throw JSONParseException(sb.toString())
      }
    }

  /**
   * @param key A key assumed to be holding a value
   * @param s   A node
   * @return An array from key `key`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getArray(
    s: ObjectNode,
    key: String
  ): ArrayNode {
    val n = getNode(s, key)
    return when (n.nodeType) {
      JsonNodeType.ARRAY -> {
        n as ArrayNode
      }

      JsonNodeType.BINARY,
      JsonNodeType.BOOLEAN,
      JsonNodeType.MISSING,
      JsonNodeType.NULL,
      JsonNodeType.NUMBER,
      JsonNodeType.POJO,
      JsonNodeType.STRING,
      JsonNodeType.OBJECT -> {
        val sb = StringBuilder(128)
        sb.append("Expected: A key '")
        sb.append(key)
        sb.append("' with a value of type Array\n")
        sb.append("Got: A value of type ")
        sb.append(n.nodeType)
        sb.append("\n")
        throw JSONParseException(sb.toString())
      }
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param s   A node
   * @return An array from key `key`, or null if the key is not present
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getArrayOrNull(
    s: ObjectNode,
    key: String
  ): ArrayNode? =
    if (s.has(key)) {
      getArray(s, key)
    } else {
      null
    }

  /**
   * @param key A key assumed to be holding a value
   * @param o   A node
   * @return A boolean value from key `key`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getBoolean(
    o: ObjectNode,
    key: String
  ): Boolean {
    val v = getNode(o, key)
    return when (v.nodeType) {
      JsonNodeType.ARRAY,
      JsonNodeType.BINARY,
      JsonNodeType.MISSING,
      JsonNodeType.NULL,
      JsonNodeType.OBJECT,
      JsonNodeType.POJO,
      JsonNodeType.STRING,
      JsonNodeType.NUMBER -> {
        val sb = StringBuilder(128)
        sb.append("Expected: A key '")
        sb.append(key)
        sb.append("' with a value of type Boolean\n")
        sb.append("Got: A value of type ")
        sb.append(v.nodeType)
        sb.append("\n")
        throw JSONParseException(sb.toString())
      }

      JsonNodeType.BOOLEAN -> {
        v.asBoolean()
      }
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return An integer value from key `key`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getInteger(
    n: ObjectNode,
    key: String
  ): Int {
    val v = getNode(n, key)
    return when (v.nodeType) {
      JsonNodeType.ARRAY,
      JsonNodeType.BINARY,
      JsonNodeType.BOOLEAN,
      JsonNodeType.MISSING,
      JsonNodeType.NULL,
      JsonNodeType.OBJECT,
      JsonNodeType.POJO,
      JsonNodeType.STRING -> {
        val sb = StringBuilder(128)
        sb.append("Expected: A key '")
        sb.append(key)
        sb.append("' with a value of type Integer\n")
        sb.append("Got: A value of type ")
        sb.append(v.nodeType)
        sb.append("\n")
        throw JSONParseException(sb.toString())
      }

      JsonNodeType.NUMBER -> {
        v.asInt()
      }
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A double value from key `key`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getDouble(
    n: ObjectNode,
    key: String
  ): Double {
    val v = getNode(n, key)
    return when (v.nodeType) {
      JsonNodeType.ARRAY,
      JsonNodeType.BINARY,
      JsonNodeType.BOOLEAN,
      JsonNodeType.MISSING,
      JsonNodeType.NULL,
      JsonNodeType.OBJECT,
      JsonNodeType.POJO,
      JsonNodeType.STRING -> {
        val sb = StringBuilder(128)
        sb.append("Expected: A key '")
        sb.append(key)
        sb.append("' with a value of type Double\n")
        sb.append("Got: A value of type ")
        sb.append(v.nodeType)
        sb.append("\n")
        throw JSONParseException(sb.toString())
      }

      JsonNodeType.NUMBER -> {
        v.asDouble()
      }
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param s   A node
   * @return An arbitrary json node from key `key`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getNode(
    s: ObjectNode,
    key: String
  ): JsonNode {
    if (s.has(key)) {
      return s[key]
    }
    val sb = StringBuilder(128)
    sb.append("Expected: A key '")
    sb.append(key)
    sb.append("'\n")
    sb.append("Got: nothing\n")
    throw JSONParseException(sb.toString())
  }

  /**
   * @param key A key assumed to be holding a value
   * @param s   A node
   * @return An object value from key `key`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getObject(
    s: ObjectNode,
    key: String
  ): ObjectNode {
    val n = getNode(s, key)
    return checkObject(key, n)
  }

  /**
   * @param key A key assumed to be holding a value
   * @param s   A node
   * @return An object value from key `key`, if the key exists
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getObjectOrNull(
    s: ObjectNode,
    key: String
  ): ObjectNode? =
    if (s.has(key)) {
      getObject(s, key)
    } else {
      null
    }

  /**
   * @param key A key assumed to be holding a value
   * @param s   A node
   * @return A string value from key `key`
   * @throws JSONParseException On type errors
   */
  @JvmStatic
  @Throws(JSONParseException::class)
  fun getString(
    s: ObjectNode,
    key: String
  ): String {
    val v = getNode(s, key)
    return when (v.nodeType) {
      JsonNodeType.ARRAY,
      JsonNodeType.BINARY,
      JsonNodeType.BOOLEAN,
      JsonNodeType.MISSING,
      JsonNodeType.NULL,
      JsonNodeType.NUMBER,
      JsonNodeType.OBJECT,
      JsonNodeType.POJO -> {
        val sb = StringBuilder(128)
        sb.append("Expected: A key '")
        sb.append(key)
        sb.append("' with a value of type String\n")
        sb.append("Got: A value of type ")
        sb.append(v.nodeType)
        sb.append("\n")
        throw JSONParseException(sb.toString())
      }

      JsonNodeType.STRING -> {
        v.asText()
      }
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return An integer value from key `key`, if the key exists
   * @throws JSONParseException On type errors
   */
  @JvmStatic
  @Throws(JSONParseException::class)
  fun getIntegerOrNull(
    n: ObjectNode,
    key: String
  ): Int? =
    if (n.has(key)) {
      getInteger(n, key)
    } else {
      null
    }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A string value from key `key`, if the key exists, or `defaultValue` otherwise.
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getIntegerDefault(
    n: ObjectNode,
    key: String,
    defaultValue: Int
  ): Int {
    val r = getIntegerOrNull(n, key)
    return if (r == null) {
      defaultValue
    } else {
      r
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return An Double value from key `key`, if the key exists
   * @throws JSONParseException On type errors
   */
  @JvmStatic
  @Throws(JSONParseException::class)
  fun getDoubleOrNull(
    n: ObjectNode,
    key: String
  ): Double? =
    if (n.has(key)) {
      getDouble(n, key)
    } else {
      null
    }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return An double value from key `key`, if the key exists, or `defaultValue` otherwise.
   * @throws JSONParseException On type errors
   */
  @JvmStatic
  @Throws(JSONParseException::class)
  fun getDoubleDefault(
    n: ObjectNode,
    key: String,
    defaultValue: Double
  ): Double {
    val r = getDoubleOrNull(n, key)
    return if (r == null) {
      defaultValue
    } else {
      r
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A string value from key `key`, if the key exists
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getStringOrNull(
    n: ObjectNode,
    key: String
  ): String? =
    if (n.has(key)) {
      if (n[key].isNull) {
        null
      } else {
        getString(n, key)
      }
    } else {
      null
    }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A string value from key `key`, if the key exists, or `defaultValue` otherwise.
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getStringDefault(
    n: ObjectNode,
    key: String,
    defaultValue: String
  ): String {
    val r = getStringOrNull(n, key)
    return if (r == null) {
      defaultValue
    } else {
      r
    }
  }

  /**
   * @param s   A node
   * @param key A key assumed to be holding a value
   * @return A timestamp value from key `key`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getTimestamp(
    s: ObjectNode,
    key: String
  ): DateTime =
    try {
      ISODateTimeFormat
        .dateTimeParser()
        .withZoneUTC()
        .parseDateTime(getString(s, key))
    } catch (e: IllegalArgumentException) {
      throw JSONParseException(
        String.format(
          "Could not parse RFC3999 date for key '%s'",
          key
        ), e
      )
    }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A timestamp value from key `key`, if the key exists
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getTimestampOrNull(
    n: ObjectNode,
    key: String
  ): DateTime? =
    if (n.has(key)) {
      if (n[key].isNull) {
        null
      } else {
        getTimestamp(n, key)
      }
    } else {
      null
    }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A URI value from key `key`, if the key exists
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getURIOrNull(
    n: ObjectNode,
    key: String
  ): URI? =
    if (n.has(key)) {
      if (n[key].isNull) {
        null
      } else {
        getURI(n, key)
      }
    } else {
      null
    }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A URI value from key `key`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getURI(
    n: ObjectNode,
    key: String
  ): URI =
    try {
      URI(getString(n, key).trim { it <= ' ' })
    } catch (e: URISyntaxException) {
      throw JSONParseException(e)
    }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A URI value from key `key`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getURIDefault(
    n: ObjectNode,
    key: String,
    defaultValue: URI
  ): URI {
    val r = getURIOrNull(n, key)
    return if (r == null) {
      defaultValue
    } else {
      r
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @param v   A default value
   * @return A boolean from key `key`, or `v` if the key does not
   * exist
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getBooleanDefault(
    n: ObjectNode,
    key: String,
    v: Boolean
  ): Boolean =
    if (n.has(key)) {
      getBoolean(n, key)
    } else {
      v
    }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A big integer value from key `key`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getBigInteger(
    n: ObjectNode,
    key: String
  ): BigInteger {
    val v = getNode(n, key)
    return when (v.nodeType) {
      JsonNodeType.ARRAY,
      JsonNodeType.BINARY,
      JsonNodeType.BOOLEAN,
      JsonNodeType.MISSING,
      JsonNodeType.NULL,
      JsonNodeType.OBJECT,
      JsonNodeType.POJO,
      JsonNodeType.STRING -> {
        val sb = StringBuilder(128)
        sb.append("Expected: A key '")
        sb.append(key)
        sb.append("' with a value of type Integer\n")
        sb.append("Got: A value of type ")
        sb.append(v.nodeType)
        sb.append("\n")
        throw JSONParseException(sb.toString())
      }

      JsonNodeType.NUMBER -> {
        try {
          BigInteger(v.asText())
        } catch (e: NumberFormatException) {
          throw JSONParseException(e)
        }
      }
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param node   A node
   * @param defaultValue The default value
   * @return A big integer value from key `key` or `defaultValue`
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getBigIntegerDefault(
    node: ObjectNode,
    key: String,
    defaultValue: BigInteger
  ): BigInteger =
    if (node.has(key)) {
      getBigInteger(node, key)
    } else {
      defaultValue
    }
}
