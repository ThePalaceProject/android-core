package org.nypl.simplified.json.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.io7m.jfunctional.None
import com.io7m.jfunctional.Option
import com.io7m.jfunctional.OptionType
import com.io7m.jfunctional.OptionVisitorType
import com.io7m.jfunctional.Some
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import java.math.BigInteger
import java.net.URI
import java.net.URISyntaxException
import java.util.Objects

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
  ): ObjectNode {
    return when (n.nodeType) {
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
  ): ArrayNode {
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
  fun checkString(
    n: JsonNode
  ): String {
    return when (n.nodeType) {
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
  ): ArrayNode? {
    return if (s.has(key)) {
      getArray(s, key)
    } else {
      null
    }
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
  fun getObjectOptional(
    s: ObjectNode,
    key: String
  ): OptionType<ObjectNode> {
    return if (s.has(key)) {
      Option.some(
        getObject(s, key)
      )
    } else {
      Option.none()
    }
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
  ): ObjectNode? {
    return if (s.has(key)) {
      getObject(s, key)
    } else {
      null
    }
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
  fun getIntegerOptional(
    n: ObjectNode,
    key: String
  ): OptionType<Int> {
    return if (n.has(key)) {
      Option.some(getInteger(n, key))
    } else {
      Option.none()
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
  ): Int? {
    return if (n.has(key)) {
      getInteger(n, key)
    } else {
      null
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A string value from key `key`, if the key exists, or `default_value` otherwise.
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getIntegerDefault(
    n: ObjectNode,
    key: String,
    default_value: Int
  ): Int {
    return getIntegerOptional(n, key).accept(
      object : OptionVisitorType<Int?, Int?> {
        override fun none(n: None<Int?>?): Int? {
          return default_value
        }

        override fun some(s: Some<Int?>?): Int? {
          return s!!.get()
        }
      })!!
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return An double value from key `key`, if the key exists
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getDoubleOptional(
    n: ObjectNode,
    key: String
  ): OptionType<Double> {
    return if (n.has(key)) {
      Option.some(getDouble(n, key))
    } else {
      Option.none()
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return An double value from key `key`, if the key exists, or `default_value` otherwise.
   * @throws JSONParseException On type errors
   */
  @JvmStatic
  @Throws(JSONParseException::class)
  fun getDoubleDefault(
    n: ObjectNode,
    key: String,
    default_value: Double
  ): Double {
    return getDoubleOptional(n, key).accept(
      object : OptionVisitorType<Double?, Double?> {
        override fun none(n: None<Double?>?): Double? {
          return default_value
        }

        override fun some(s: Some<Double?>?): Double? {
          return s!!.get()
        }
      })!!
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A string value from key `key`, if the key exists
   * @throws JSONParseException On type errors
   */
  @JvmStatic
  @Throws(JSONParseException::class)
  fun getStringOptional(
    n: ObjectNode,
    key: String
  ): OptionType<String> {
    return if (n.has(key)) {
      if (n[key].isNull) {
        Option.none()
      } else {
        Option.some(getString(n, key))
      }
    } else {
      Option.none()
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
  ): String? {
    return if (n.has(key)) {
      if (n[key].isNull) {
        null
      } else {
        getString(n, key)
      }
    } else {
      null
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A string value from key `key`, if the key exists, or `default_value` otherwise.
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getStringDefault(
    n: ObjectNode,
    key: String,
    default_value: String
  ): String {
    return getStringOptional(n, key).accept(
      object : OptionVisitorType<String?, String?> {
        override fun none(n: None<String?>?): String? {
          return default_value
        }

        override fun some(s: Some<String?>?): String? {
          return s!!.get()
        }
      })!!
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
  ): DateTime {
    return try {
      ISODateTimeFormat.dateTimeParser()
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
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A timestamp value from key `key`, if the key exists
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getTimestampOptional(
    n: ObjectNode,
    key: String
  ): OptionType<DateTime> {
    return if (n.has(key)) {
      Option.some(
        getTimestamp(
          n,
          key
        )
      )
    } else {
      Option.none()
    }
  }

  /**
   * @param key A key assumed to be holding a value
   * @param n   A node
   * @return A URI value from key `key`, if the key exists
   * @throws JSONParseException On type errors
   */

  @JvmStatic
  @Throws(JSONParseException::class)
  fun getURIOptional(
    n: ObjectNode,
    key: String
  ): OptionType<URI> {
    return getStringOptional(n, key).mapPartial<URI, JSONParseException> { x: String? ->
      try {
        return@mapPartial URI(x)
      } catch (e: URISyntaxException) {
        throw JSONParseException(e)
      }
    }
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
  ): URI? {
    val opt = getURIOptional(n, key)
    return if (opt.isSome) {
      (opt as Some<URI>).get()
    } else {
      null
    }
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
  ): URI {
    return try {
      URI(getString(n, key).trim { it <= ' ' })
    } catch (e: URISyntaxException) {
      throw JSONParseException(e)
    }
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
    default_value: URI
  ): URI {
    Objects.requireNonNull(default_value, "Default")
    return getURIOptional(n, key).accept(object : OptionVisitorType<URI?, URI?> {
      override fun none(n: None<URI?>?): URI? {
        return default_value
      }

      override fun some(s: Some<URI?>?): URI? {
        return s!!.get()
      }
    })!!
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
  ): Boolean {
    return if (n.has(key)) {
      getBoolean(n, key)
    } else {
      v
    }
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
  ): BigInteger {
    return if (node.has(key)) {
      getBigInteger(node, key)
    } else {
      defaultValue
    }
  }
}
