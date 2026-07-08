package org.nypl.simplified.opds.core

import org.joda.time.DateTime
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.OutputStream
import java.net.URI
import java.net.URISyntaxException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerConfigurationException
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.TransformerFactoryConfigurationError
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Convenient XML handling functions.
 */
object OPDSXML {
  /**
   * Return all child elements of `node` that have name `name` in
   * namespace `namespace`.
   *
   * @param node      The parent node
   * @param namespace The namespace
   * @param name      The element name
   * @return A list of elements
   */
  fun getChildElementsWithName(
    node: Element,
    namespace: URI,
    name: String
  ): MutableList<Element> {
    val namespaceText: String = namespace.toString()
    val children = node.childNodes
    val xs: MutableList<Element> = ArrayList(children.length)
    for (index in 0..<children.length) {
      val child = children.item(index)
      if (child is Element) {
        val childElement = child
        val childNamespace = childElement.namespaceURI
        val childName = childElement.localName
        if (childNamespace == namespaceText && childName == name) {
          xs.add(childElement)
        }
      }
    }

    return xs
  }

  /**
   * Return all child elements of `node` that have name `name` in
   * namespace `namespace`.
   *
   * @param node      The parent node
   * @param namespace The namespace
   * @param name      The element name
   * @return A list of elements
   * @throws OPDSParseException If there are no matching elements
   */
  @Throws(OPDSParseException::class)
  fun getChildElementsWithNameNonEmpty(
    node: Element,
    namespace: URI,
    name: String
  ): MutableList<Element> {
    val elements: MutableList<Element> = getChildElementsWithName(node, namespace, name)
    if (!elements.isEmpty()) {
      return elements
    }

    val m = StringBuilder(128)
    m.append("Missing at least one required element.\n")
    m.append("Expected namespace: ")
    m.append(namespace)
    m.append("\n")
    m.append("Expected name:      ")
    m.append(name)
    m.append("\n")
    throw OPDSParseException(m.toString())
  }

  /**
   * Return all child elements of `node` that have any of the names `names`.
   *
   * @param node  The parent node
   * @param names The names
   * @return A list of elements
   */
  fun getChildElementsWithNames(
    node: Element,
    names: MutableSet<MutableMap.MutableEntry<URI, String>>
  ): MutableList<Element> {
    val children = node.childNodes
    val xs: MutableList<Element> = ArrayList(children.length)
    for (index in 0..<children.length) {
      val child = children.item(index)
      if (child is Element) {
        val child_element = child
        val child_namespace: URI
        try {
          child_namespace = URI(child_element.namespaceURI)
        } catch (e: URISyntaxException) {
          continue
        }
        val child_name =
          child_element.localName

        for (requested in names) {
          if (requested.key == child_namespace && requested.value == child_name) {
            xs.add(child_element)
          }
        }
      }
    }
    return xs
  }

  /**
   * Return all child elements of `node` that have any of the names `names`.
   *
   * @param node  The parent node
   * @param names The names
   * @return A list of elements
   */
  @Throws(OPDSParseException::class)
  fun getChildElementsWithNamesNonEmpty(
    node: Element,
    names: MutableSet<MutableMap.MutableEntry<URI, String>>
  ): MutableList<Element> {
    val elements: MutableList<Element> = getChildElementsWithNames(node, names)
    if (!elements.isEmpty()) {
      return elements
    }
    val m = StringBuilder(128)
    m.append("Missing at least one required element.\n")
    m.append("Expected: ")
    m.append(names)
    m.append("\n")
    throw OPDSParseException(m.toString())
  }

  /**
   * Return the text of the first child element of `node` that has name
   * `name` in namespace `namespace`.
   *
   * @param node      The node
   * @param namespace The child namespace
   * @param name      The child name
   * @return The text of the child element
   * @throws OPDSParseException If there are no matching child elements
   */
  @JvmStatic
  @Throws(OPDSParseException::class)
  fun getFirstChildElementTextWithName(
    node: Element,
    namespace: URI,
    name: String
  ): String {
    val e: Element = getFirstChildElementWithName(node, namespace, name)
    return e.textContent.trim { it <= ' ' }
  }

  /**
   * Return the (optional) text of the first child element of `node` that
   * has name `name` in namespace `namespace`.
   *
   * @param node      The node
   * @param namespace The child namespace
   * @param name      The child name
   * @return The text of the child element, if any
   */
  fun getFirstChildElementTextWithNameOptional(
    node: Element,
    namespace: URI,
    name: String
  ): String? {
    val children = node.childNodes
    for (index in 0..<children.length) {
      val child = children.item(index)
      if (child is Element) {
        if (nodeHasName(child, namespace, name)) {
          val text = child.textContent
          return text.trim { it <= ' ' }
        }
      }
    }
    return null
  }

  /**
   * Return the (optional) text of the first child element of `node` that
   * has name `name` in namespace `namespace`.
   *
   * @param node      The node
   * @param namespace The child namespace
   * @param name      The child name
   * @param attribute The attribute name
   * @return The text of the child element, if any
   */
  fun getFirstChildElementTextWithName(
    node: Element,
    namespace: URI,
    name: String,
    attribute: String
  ): String {
    val children = node.childNodes
    for (index in 0..<children.length) {
      val child = children.item(index)
      if (child is Element) {
        if (nodeHasName(child, namespace, name)) {
          val text =
            child.attributes
              .getNamedItemNS(namespace.toString(), attribute)
              .nodeValue
          return text.trim { it <= ' ' }
        }
      }
    }
    return ""
  }

  /**
   * Return the first child element of `node` that has name `name`
   * in namespace `namespace`.
   *
   * @param node      The node
   * @param namespace The child namespace
   * @param name      The child name
   * @return The child element
   * @throws OPDSParseException If no matching element exists
   */
  @JvmStatic
  @Throws(OPDSParseException::class)
  fun getFirstChildElementWithName(
    node: Element,
    namespace: URI,
    name: String
  ): Element {
    val children = node.childNodes
    for (index in 0..<children.length) {
      val child = children.item(index)
      if (child is Element) {
        if (nodeHasName(child, namespace, name)) {
          return child
        }
      }
    }

    val m = StringBuilder(128)
    m.append("Expected required element.\n")
    m.append("Expected namespace: ")
    m.append(namespace)
    m.append("\n")
    m.append("Expected name:      ")
    m.append(name)
    m.append("\n")
    throw OPDSParseException(m.toString())
  }

  /**
   * Return the first child element of `node` that has name `name`
   * in namespace `namespace`, if any.
   *
   * @param node      The node
   * @param namespace The child namespace
   * @param name      The child name
   * @return The child element, if any
   */
  @JvmStatic
  fun getFirstChildElementWithNameOptional(
    node: Element,
    namespace: URI,
    name: String
  ): Element? {
    val children = node.childNodes
    for (index in 0..<children.length) {
      val child = children.item(index)
      if (child is Element) {
        if (nodeHasName(child, namespace, name)) {
          return child
        }
      }
    }
    return null
  }

  /**
   * @param e The element
   * @return The namespace of the given element, if any
   */
  @JvmStatic
  fun getNodeNamespace(e: Element): String? {
    val ns = e.namespaceURI
    if (ns != null) {
      return ns
    }
    return null
  }

  /**
   * Cast the given node to an [Element], raising an exception if it is
   * not an element.
   *
   * @param node The node
   * @return The node as an element
   * @throws OPDSParseException If the node is not an [Element]
   */
  @JvmStatic
  @Throws(OPDSParseException::class)
  fun nodeAsElement(node: Node): Element {
    if (node !is Element) {
      val m = StringBuilder(128)
      m.append("Expected element but got node of type ")
      m.append(node.nodeName)
      throw OPDSParseException(m.toString())
    }
    return node
  }

  /**
   * Cast the given node to an [Element], raising an exception if it is
   * not an element and/or does not have the given `name` and `namespace`.
   *
   * @param node      The node
   * @param name      The expected element name
   * @param namespace The expected element namespace
   * @return The node as an element
   * @throws OPDSParseException If the node is not an [Element] or has the
   * wrong name
   */
  @JvmStatic
  @Throws(OPDSParseException::class)
  fun nodeAsElementWithName(
    node: Node,
    namespace: URI,
    name: String
  ): Element {
    val e: Element = nodeAsElement(node)
    if (nodeHasName(e, namespace, name)) {
      return e
    }

    val m = StringBuilder(128)
    m.append("Missing required element.\n")
    m.append("Expected namespace: ")
    m.append(namespace)
    m.append("\n")
    m.append("Expected name:      ")
    m.append(name)
    m.append("\n")
    m.append("Got namespace:      ")
    m.append(getNodeNamespace(e))
    m.append("\n")
    m.append("Got name:           ")
    m.append(e.nodeName)
    m.append("\n")
    throw OPDSParseException(m.toString())
  }

  /**
   * @param node      The element
   * @param namespace The namespace
   * @param name      The name
   * @return `true` if the given element has the given name and namespace
   */
  @JvmStatic
  fun nodeHasName(
    node: Element,
    namespace: URI,
    name: String
  ): Boolean {
    val nodeLocal = node.localName
    if (nodeLocal == name) {
      return namespace.toString() == node.namespaceURI
    }
    return false
  }

  /**
   * Parse the contents of attribute `name` of element `e` as an
   * RFC3339 date, if the attribute exists.
   *
   * @param e    The element
   * @param name The attribute name
   * @return A date, if any
   * @throws OPDSParseException On parse errors
   */
  @Throws(OPDSParseException::class)
  fun getAttributeRFC3339Optional(
    e: Element,
    name: String
  ): DateTime? {
    try {
      if (e.hasAttribute(name)) {
        return OPDSDateParsers.dateTimeParser().parseDateTime(e.getAttribute(name))
      }
      return null
    } catch (x: Exception) {
      throw OPDSParseException(x)
    }
  }

  /**
   * Parse the contents of attribute `name` of element `e` as an
   * RFC3339 date.
   *
   * @param e    The element
   * @param name The attribute name
   * @return A date
   * @throws OPDSParseException On parse errors
   */
  @Throws(OPDSParseException::class)
  fun getAttributeRFC3339(
    e: Element,
    name: String
  ): DateTime {
    if (e.hasAttribute(name)) {
      try {
        return OPDSDateParsers
          .dateTimeParser()
          .parseDateTime(e.getAttribute(name))
      } catch (x: Exception) {
        throw OPDSParseException(x)
      }
    }

    val m = StringBuilder(128)
    m.append("Expected required attribute.\n")
    m.append("Expected name:      ")
    m.append(name)
    m.append("\n")
    throw OPDSParseException(m.toString())
  }

  /**
   * Convenient function to serialize the given document to the given output
   * stream.
   *
   * @param d The document
   * @param o The output stream
   * @throws OPDSSerializationException If any errors occur on serialization
   */
  @Throws(OPDSSerializationException::class)
  fun serializeDocumentToStream(
    d: Document,
    o: OutputStream
  ) {
    try {
      val tf = TransformerFactory.newInstance()
      val t = tf.newTransformer()
      t.setOutputProperty(OutputKeys.INDENT, "yes")
      t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

      val source = DOMSource(d)
      val target = StreamResult(o)
      t.transform(source, target)
    } catch (ex: TransformerConfigurationException) {
      throw OPDSSerializationException(ex)
    } catch (ex: TransformerFactoryConfigurationError) {
      throw OPDSSerializationException(ex)
    } catch (ex: TransformerException) {
      throw OPDSSerializationException(ex)
    }
  }

  /**
   * Parse the contents of attribute `name` of element `e` as an
   * integer, if the attribute exists.
   *
   * @param e    The element
   * @param name The attribute name
   * @return An integer, if any
   * @throws OPDSParseException On parse errors
   */
  @Throws(OPDSParseException::class)
  fun getAttributeIntegerOptional(
    e: Element,
    name: String
  ): Int? {
    if (e.hasAttribute(name)) {
      try {
        return e.getAttribute(name).toInt()
      } catch (x: NumberFormatException) {
        throw OPDSParseException(x)
      }
    }
    return null
  }

  /**
   * Parse the contents of attribute `name` of element `e` as an
   * integer.
   *
   * @param e    The element
   * @param name The attribute name
   * @return An integer
   * @throws OPDSParseException On parse errors
   */
  @Throws(OPDSParseException::class)
  fun getAttributeInteger(
    e: Element,
    name: String
  ): Int {
    if (e.hasAttribute(name)) {
      try {
        return e.getAttribute(name).toInt()
      } catch (x: NumberFormatException) {
        throw OPDSParseException(x)
      }
    }

    val m = StringBuilder(128)
    m.append("Expected required attribute.\n")
    m.append("Expected name:      ")
    m.append(name)
    m.append("\n")
    throw OPDSParseException(m.toString())
  }
}
