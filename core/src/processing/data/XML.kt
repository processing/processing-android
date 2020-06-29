/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */ /*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012 The Processing Foundation
  Copyright (c) 2009-12 Ben Fry and Casey Reas

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty
  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/
package processing.data

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import processing.core.PApplet
import java.io.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.Source
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * This is the base class used for the Processing XML library,
 * representing a single node of an XML tree.
 *
 * @webref data:composite
 * @see PApplet.loadXML
 * @see PApplet.parseXML
 * @see PApplet.saveXML
 */
open class XML : Serializable {
    /** The internal representation, a DOM node.  */
    private var node: Node? = null
    //  /** Cached locally because it's used often. */
    //  protected String name;
    /**
     * Returns the parent element. This method returns null for the root
     * element.
     *
     * @webref xml:method
     * @brief Gets a copy of the element's parent
     */
    /** The parent element.  */ // nullable
    var parent: XML? = null
        protected set

    /** Child elements, once loaded.  */ // nullable
    private var children: Array<XML?>? = null

    /**
     * @nowebref
     */
    protected constructor() {}
    /**
     * Advanced users only; use loadXML() in PApplet.
     *
     * @nowebref
     */
    //  /**
    //   * Begin parsing XML data passed in from a PApplet. This code
    //   * wraps exception handling, for more advanced exception handling,
    //   * use the constructor that takes a Reader or InputStream.
    //   *
    //   * @throws SAXException
    //   * @throws ParserConfigurationException
    //   * @throws IOException
    //   */
    //  public XML(PApplet parent, String filename) throws IOException, ParserConfigurationException, SAXException {
    //    this(parent.createReader(filename));
    //  }
    /**
     * Advanced users only; use loadXML() in PApplet. This is not a supported
     * function and is subject to change. It is available simply for users that
     * would like to handle the exceptions in a particular way.
     *
     * @nowebref
     */
    @JvmOverloads
    constructor(file: File?, options: String? = null) : this(PApplet.createReader(file) as Reader?, options) {
    }
    /**
     * Unlike the loadXML() method in PApplet, this version works with files
     * that are not in UTF-8 format.
     *
     * @nowebref
     */
    /**
     * @nowebref
     */
    @JvmOverloads
    constructor(input: InputStream?, options: String? = null) {
        //this(PApplet.createReader(input), options);  // won't handle non-UTF8
        val factory = DocumentBuilderFactory.newInstance()
        try {
            // Prevent 503 errors from www.w3.org
            factory.setAttribute("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        } catch (e: IllegalArgumentException) {
            // ignore this; Android doesn't like it
        }
        factory.isExpandEntityReferences = false
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(input))
        node = document.documentElement
    }
    /**
     * Advanced users only; use loadXML() in PApplet.
     *
     * Added extra code to handle \u2028 (Unicode NLF), which is sometimes
     * inserted by web browsers (Safari?) and not distinguishable from a "real"
     * LF (or CRLF) in some text editors (i.e. TextEdit on OS X). Only doing
     * this for XML (and not all Reader objects) because LFs are essential.
     * https://github.com/processing/processing/issues/2100
     *
     * @nowebref
     */
    /**
     * Advanced users only; use loadXML() in PApplet.
     *
     * @nowebref
     */
    @JvmOverloads
    constructor(reader: Reader?, options: String? = null) {
        val factory = DocumentBuilderFactory.newInstance()

        // Prevent 503 errors from www.w3.org
        try {
            factory.setAttribute("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        } catch (e: IllegalArgumentException) {
            // ignore this; Android doesn't like it
        }

        // without a validating DTD, this doesn't do anything since it doesn't know what is ignorable
//      factory.setIgnoringElementContentWhitespace(true);
        factory.isExpandEntityReferences = false
        //      factory.setExpandEntityReferences(true);

//      factory.setCoalescing(true);
//      builderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        val builder = factory.newDocumentBuilder()
        //      builder.setEntityResolver()

//      SAXParserFactory spf = SAXParserFactory.newInstance();
//      spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
//      SAXParser p = spf.newSAXParser();

        //    builder = DocumentBuilderFactory.newDocumentBuilder();
        //    builder = new SAXBuilder();
        //    builder.setValidation(validating);
        val document = builder.parse(InputSource(object : Reader() {
            @Throws(IOException::class)
            override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                val count = reader!!.read(cbuf, off, len)
                for (i in 0 until count) {
                    if (cbuf[off + i] == '\u2028') {
                        cbuf[off + i] = '\n'
                    }
                }
                return count
            }

            @Throws(IOException::class)
            override fun close() {
                reader!!.close()
            }
        }))
        node = document.documentElement
    }

    /**
     * @param name creates a node with this name
     */
    constructor(name: String?) {
        try {
            // TODO is there a more efficient way of doing this? wow.
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.newDocument()
            node = document.createElement(name)
            parent = null
        } catch (pce: ParserConfigurationException) {
            throw RuntimeException(pce)
        }
    }

    /**
     * @nowebref
     */
    protected constructor(parent: XML, node: Node?) {
        this.node = node
        this.parent = parent
        for (attr in parent.listAttributes()) {
            if (attr!!.startsWith("xmlns")) {
                // Copy namespace attributes to the kids, otherwise this XML
                // can no longer be printed (or manipulated in most ways).
                // Only do this when it's an Element, otherwise it's trying to set
                // attributes on text notes (interstitial content).
                if (node is Element) {
                    setString(attr, parent.getString(attr))
                }
            }
        }
    }

    // not checking nullability i.e. non-nullable in use -> ranaaditya
    //  protected boolean save(OutputStream output) {
    //    return write(PApplet.createWriter(output));
    //  }
    @JvmOverloads
    fun save(file: File?, options: String? = null): Boolean {
        val writer = PApplet.createWriter(file)
        val result = write(writer)
        writer.flush()
        writer.close()
        return result
    }

    // Sends this object and its kids to a Writer with an indent of 2 spaces,
    // including the declaration at the top so that the output will be valid XML.
    // Non-nullable -> ranaaditya
    fun write(output: PrintWriter): Boolean {
        output.print(format(2))
        output.flush()
        return true
    }

    /**
     * Internal function; not included in reference.
     */
    protected val native: Any?
        get() = node//    return name;//    name = node.getNodeName();

    /**
     * @webref xml:method
     * @brief Sets the element's name
     */
    /**
     * Returns the full name (i.e. the name including an eventual namespace
     * prefix) of the element.
     *
     * @webref xml:method
     * @brief Gets the element's full name
     * @return the name, or null if the element only contains #PCDATA.
     */
    var name: String?
        get() =//    return name;
            node!!.nodeName
        set(newName) {
            val document = node!!.ownerDocument
            node = document.renameNode(node, null, newName)
            //    name = node.getNodeName();
        }

    /**
     * Returns the name of the element (without namespace prefix).
     *
     * Internal function; not included in reference.
     */
    val localName: String
        get() = node!!.localName

    /**
     * Honey, can you just check on the kids? Thanks.
     *
     * Internal function; not included in reference.
     */
    protected fun checkChildren() {
        if (children == null) {
            val kids = node!!.childNodes
            val childCount = kids.length
            children = arrayOfNulls(childCount)
            for (i in 0 until childCount) {
                children!![i] = XML(this, kids.item(i))
            }
        }
    }

    /**
     * Returns the number of children.
     *
     * @webref xml:method
     * @brief Returns the element's number of children
     * @return the count.
     */
    val childCount: Int
        get() {
            checkChildren()
            return children!!.size
        }

    /**
     * Returns a boolean of whether or not there are children.
     *
     * @webref xml:method
     * @brief Checks whether or not an element has any children
     */
    fun hasChildren(): Boolean {
        checkChildren()
        return children!!.size > 0
    }

    /**
     * Put the names of all children into an array. Same as looping through
     * each child and calling getName() on each XMLElement.
     *
     * @webref xml:method
     * @brief Returns the names of all children as an array
     */
    fun listChildren(): Array<String?> {
//    NodeList children = node.getChildNodes();
//    int childCount = children.getLength();
//    String[] outgoing = new String[childCount];
//    for (int i = 0; i < childCount; i++) {
//      Node kid = children.item(i);
//      if (kid.getNodeType() == Node.ELEMENT_NODE) {
//        outgoing[i] = kid.getNodeName();
//      } // otherwise just leave him null
//    }
        checkChildren()
        val outgoing = arrayOfNulls<String>(children!!.size)
        for (i in children!!.indices) {
            outgoing[i] = children!![i]!!.name
        }
        return outgoing
    }

    /**
     * Returns an array containing all the child elements.
     *
     * @webref xml:method
     * @brief Returns an array containing all child elements
     */
    fun getChildren(): Array<XML?>? {
//    NodeList children = node.getChildNodes();
//    int childCount = children.getLength();
//    XMLElement[] kids = new XMLElement[childCount];
//    for (int i = 0; i < childCount; i++) {
//      Node kid = children.item(i);
//      kids[i] = new XMLElement(this, kid);
//    }
//    return kids;
        checkChildren()
        return children
    }

    /**
     * Quick accessor for an element at a particular index.
     *
     * @webref xml:method
     * @brief Returns the child element with the specified index value or path
     */
    fun getChild(index: Int): XML? {
        checkChildren()
        return children!![index]
    }

    /**
     * Get a child by its name or path.
     *
     * @param name element name or path/to/element
     * @return the first matching element or null if no match
     */
    fun getChild(name: String): XML? {
        require(!(name.length > 0 && name[0] == '/')) { "getChild() should not begin with a slash" }
        if (name.indexOf('/') != -1) {
            return getChildRecursive(PApplet.split(name, '/'), 0)
        }
        val childCount = childCount
        for (i in 0 until childCount) {
            val kid = getChild(i)
            val kidName = kid!!.name
            if (kidName != null && kidName == name) {
                return kid
            }
        }
        return null
    }

    /**
     * Internal helper function for getChild(String).
     *
     * @param items result of splitting the query on slashes
     * @param offset where in the items[] array we're currently looking
     * @return matching element or null if no match
     * @author processing.org
     */
    protected fun getChildRecursive(items: Array<String>, offset: Int): XML? {
        // if it's a number, do an index instead
        if (Character.isDigit(items[offset][0])) {
            val kid = getChild(items[offset].toInt())
            return if (offset == items.size - 1) {
                kid
            } else {
                kid!!.getChildRecursive(items, offset + 1)
            }
        }
        val childCount = childCount
        for (i in 0 until childCount) {
            val kid = getChild(i)
            val kidName = kid!!.name
            if (kidName != null && kidName == items[offset]) {
                return if (offset == items.size - 1) {
                    kid
                } else {
                    kid.getChildRecursive(items, offset + 1)
                }
            }
        }
        return null
    }

    /**
     * Get any children that match this name or path. Similar to getChild(),
     * but will grab multiple matches rather than only the first.
     *
     * @param name element name or path/to/element
     * @return array of child elements that match
     * @author processing.org
     */
    fun getChildren(name: String?): Array<XML?> {
        require(!(name!!.isNotEmpty() && name[0] == '/')) { "getChildren() should not begin with a slash" }
        if (name.indexOf('/') != -1) {
            return getChildrenRecursive(PApplet.split(name, '/'), 0)
        }
        // if it's a number, do an index instead
        // (returns a single element array, since this will be a single match
        if (Character.isDigit(name[0])) {
            return arrayOf(getChild(name.toInt()))
        }
        val childCount = childCount
        val matches = arrayOfNulls<XML>(childCount)
        var matchCount = 0
        for (i in 0 until childCount) {
            val kid = getChild(i)
            val kidName = kid!!.name
            if (kidName != null && kidName == name) {
                matches[matchCount++] = kid
            }
        }
        return PApplet.subset(matches, 0, matchCount) as Array<XML?>
    }

    protected fun getChildrenRecursive(items: Array<String>, offset: Int): Array<XML?> {
        if (offset == items.size - 1) {
            return getChildren(items[offset])
        }
        val matches = getChildren(items[offset])
        var outgoing = arrayOfNulls<XML>(0)
        for (i in matches.indices) {
            val kidMatches = matches[i]!!.getChildrenRecursive(items, offset + 1)
            outgoing = PApplet.concat(outgoing, kidMatches) as Array<XML?>
        }
        return outgoing
    }

    /**
     * @webref xml:method
     * @brief Appends a new child to the element
     */
    fun addChild(tag: String?): XML {
        val document = node!!.ownerDocument
        val newChild: Node = document.createElement(tag)
        return appendChild(newChild)
    }

    fun addChild(child: XML): XML {
        val document = node!!.ownerDocument
        val newChild = document.importNode(child.native as Node?, true)
        return appendChild(newChild)
    }

    /** Internal handler to add the node structure.  */
    protected fun appendChild(newNode: Node?): XML {
        node!!.appendChild(newNode)
        val newbie = XML(this, newNode)
        if (children != null) {
            children = PApplet.concat(children, arrayOf(newbie)) as Array<XML?>
        }
        return newbie
    }

    /**
     * @webref xml:method
     * @brief Removes the specified child
     */
    fun removeChild(kid: XML) {
        node!!.removeChild(kid.node)
        children = null // TODO not efficient
    }

    /**
     * Removes whitespace nodes.
     * Those whitespace nodes are required to reconstruct the original XML's spacing and indentation.
     * If you call this and use saveXML() your original spacing will be gone.
     *
     * @nowebref
     * @brief Removes whitespace nodes
     */
    fun trim() {
        try {
            val xpathFactory = XPathFactory.newInstance()
            val xpathExp = xpathFactory.newXPath().compile("//text()[normalize-space(.) = '']")
            val emptyTextNodes = xpathExp.evaluate(node, XPathConstants.NODESET) as NodeList

            // Remove each empty text node from document.
            for (i in 0 until emptyTextNodes.length) {
                val emptyTextNode = emptyTextNodes.item(i)
                emptyTextNode.parentNode.removeChild(emptyTextNode)
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    //  /** Remove whitespace nodes. */
    //  public void trim() {
    //////    public static boolean isWhitespace(XML xml) {
    //////      if (xml.node.getNodeType() != Node.TEXT_NODE)
    //////        return false;
    //////      Matcher m = whitespace.matcher(xml.node.getNodeValue());
    //////      return m.matches();
    //////    }
    ////    trim(this);
    ////  }
    //
    //    checkChildren();
    //    int index = 0;
    //    for (int i = 0; i < children.length; i++) {
    //      if (i != index) {
    //        children[index] = children[i];
    //      }
    //      Node childNode = (Node) children[i].getNative();
    //      if (childNode.getNodeType() != Node.TEXT_NODE ||
    //          children[i].getContent().trim().length() > 0) {
    //        children[i].trim();
    //        index++;
    //      }
    //    }
    //    if (index != children.length) {
    //      children = (XML[]) PApplet.subset(children, 0, index);
    //    }
    //
    //    // possibility, but would have to re-parse the object
    //// helpdesk.objects.com.au/java/how-do-i-remove-whitespace-from-an-xml-document
    ////    TransformerFactory factory = TransformerFactory.newInstance();
    ////    Transformer transformer = factory.newTransformer(new StreamSource("strip-space.xsl"));
    ////    DOMSource source = new DOMSource(document);
    ////    StreamResult result = new StreamResult(System.out);
    ////    transformer.transform(source, result);
    //
    ////    <xsl:stylesheet version="1.0"
    ////      xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    ////   <xsl:output method="xml" omit-xml-declaration="yes"/>
    ////     <xsl:strip-space elements="*"/>
    ////     <xsl:template match="@*|node()">
    ////      <xsl:copy>
    ////       <xsl:apply-templates select="@*|node()"/>
    ////      </xsl:copy>
    ////     </xsl:template>
    ////   </xsl:stylesheet>
    //  }

    /**
     * Returns the number of attributes.
     *
     * @webref xml:method
     * @brief Counts the specified element's number of attributes
     */
    val attributeCount: Int
        get() = node!!.attributes.length

    /**
     * Get a list of the names for all of the attributes for this node.
     *
     * @webref xml:method
     * @brief Returns a list of names of all attributes as an array
     */
    fun listAttributes(): Array<String?> {
        val nnm = node!!.attributes
        val outgoing = arrayOfNulls<String>(nnm.length)
        for (i in outgoing.indices) {
            outgoing[i] = nnm.item(i).nodeName
        }
        return outgoing
    }

    /**
     * Returns whether an attribute exists.
     *
     * @webref xml:method
     * @brief Checks whether or not an element has the specified attribute
     */
    fun hasAttribute(name: String?): Boolean {
        return node!!.attributes.getNamedItem(name) != null
    }
    /**
     * Returns the value of an attribute.
     *
     * @param name the non-null name of the attribute.
     * @return the value, or null if the attribute does not exist.
     */
    //  public String getAttribute(String name) {
    //    return this.getAttribute(name, null);
    //  }
    /**
     * Returns the value of an attribute.
     *
     * @param name the non-null full name of the attribute.
     * @param defaultValue the default value of the attribute.
     * @return the value, or defaultValue if the attribute does not exist.
     */
    //  public String getAttribute(String name, String defaultValue) {
    //    Node attr = node.getAttributes().getNamedItem(name);
    //    return (attr == null) ? defaultValue : attr.getNodeValue();
    //  }
    /**
     * @webref xml:method
     * @brief Gets the content of an attribute as a String
     */
    fun getString(name: String?): String? {
        return getString(name, null)
    }

    fun getString(name: String?, defaultValue: String?): String? {
        val attrs = node!!.attributes
        if (attrs != null) {
            val attr = attrs.getNamedItem(name)
            if (attr != null) {
                return attr.nodeValue
            }
        }
        return defaultValue
    }

    /**
     * @webref xml:method
     * @brief Sets the content of an attribute as a String
     */
    fun setString(name: String?, value: String?) {
        (node as Element?)!!.setAttribute(name, value)
    }

    /**
     * @webref xml:method
     * @brief Gets the content of an attribute as an int
     */
    fun getInt(name: String?): Int {
        return getInt(name, 0)
    }

    /**
     * @webref xml:method
     * @brief Sets the content of an attribute as an int
     */
    fun setInt(name: String?, value: Int) {
        setString(name, value.toString())
    }

    /**
     * Returns the value of an attribute.
     *
     * @param name the non-null full name of the attribute
     * @param defaultValue the default value of the attribute
     * @return the value, or defaultValue if the attribute does not exist
     */
    fun getInt(name: String?, defaultValue: Int): Int {
        val value = getString(name)
        return value?.toInt() ?: defaultValue
    }

    /**
     * @webref xml:method
     * @brief Sets the content of an element as an int
     */
    fun setLong(name: String?, value: Long) {
        setString(name, value.toString())
    }

    /**
     * Returns the value of an attribute.
     *
     * @param name the non-null full name of the attribute.
     * @param defaultValue the default value of the attribute.
     * @return the value, or defaultValue if the attribute does not exist.
     */
    fun getLong(name: String?, defaultValue: Long): Long {
        val value = getString(name)
        return value?.toLong() ?: defaultValue
    }

    /**
     * Returns the value of an attribute, or zero if not present.
     *
     * @webref xml:method
     * @brief Gets the content of an attribute as a float
     */
    fun getFloat(name: String?): Float {
        return getFloat(name, 0f)
    }

    /**
     * Returns the value of an attribute.
     *
     * @param name the non-null full name of the attribute.
     * @param defaultValue the default value of the attribute.
     * @return the value, or defaultValue if the attribute does not exist.
     */
    fun getFloat(name: String?, defaultValue: Float): Float {
        val value = getString(name)
        return value?.toFloat() ?: defaultValue
    }

    /**
     * @webref xml:method
     * @brief Sets the content of an attribute as a float
     */
    fun setFloat(name: String?, value: Float) {
        setString(name, value.toString())
    }

    fun getDouble(name: String?): Double {
        return getDouble(name, 0.0)
    }

    /**
     * Returns the value of an attribute.
     *
     * @param name the non-null full name of the attribute
     * @param defaultValue the default value of the attribute
     * @return the value, or defaultValue if the attribute does not exist
     */
    fun getDouble(name: String?, defaultValue: Double): Double {
        val value = getString(name)
        return value?.toDouble() ?: defaultValue
    }

    fun setDouble(name: String?, value: Double) {
        setString(name, value.toString())
    }

//    var content: String?
//        get() = node!!.textContent

    /**
     * Return the #PCDATA content of the element. If the element has a
     * combination of #PCDATA content and child elements, the #PCDATA
     * sections can be retrieved as unnamed child objects. In this case,
     * this method returns null.
     *
     * @webref xml:method
     * @brief Gets the content of an element
     * @return the content.
     * @see XML.getIntContent
     * @see XML.getFloatContent
     */
    fun getContent(): String {
        return node!!.textContent
    }

    fun getContent(defaultValue: String?): String {
        val s = node!!.textContent
        return s ?: defaultValue!!
    }

    /**
     * @webref xml:method
     * @brief Gets the content of an element as an int
     * @return the content.
     * @see XML.getContent
     * @see XML.getFloatContent
     */
    fun getIntContent(): Int {
        return getIntContent(0)
    }

    /**
     * @param defaultValue the default value of the attribute
     */
    fun getIntContent(defaultValue: Int): Int {
        return PApplet.parseInt(node!!.textContent, defaultValue)
    }

    /**
     * @webref xml:method
     * @brief Gets the content of an element as a float
     * @return the content.
     * @see XML.getContent
     * @see XML.getIntContent
     */
    fun getFloatContent(): Float {
        return getFloatContent(0f)
    }

    /**
     * @param defaultValue the default value of the attribute
     */
    fun getFloatContent(defaultValue: Float): Float {
        return PApplet.parseFloat(node!!.textContent, defaultValue)
    }

    fun getLongContent(): Long {
        return getLongContent(0)
    }

    fun getLongContent(defaultValue: Long): Long {
        val c = node!!.textContent
        if (c != null) {
            try {
                return c.toLong()
            } catch (nfe: NumberFormatException) {
            }
        }
        return defaultValue
    }

    fun getDoubleContent(): Double {
        return getDoubleContent(0.0)
    }

    fun getDoubleContent(defaultValue: Double): Double {
        val c = node!!.textContent
        if (c != null) {
            try {
                return c.toDouble()
            } catch (nfe: NumberFormatException) {
            }
        }
        return defaultValue
    }

    /**
     * @webref xml:method
     * @brief Sets the content of an element
     */
    fun setContent(text: String?) {
        node!!.textContent = text
    }

    fun setIntContent(value: Int) {
        setContent(value.toString())
    }

    fun setFloatContent(value: Float) {
        setContent(value.toString())
    }

    fun setLongContent(value: Long) {
        setContent(value.toString())
    }

    fun setDoubleContent(value: Double) {
        setContent(value.toString())
    }

    /**
     * Format this XML data as a String.
     *
     * @webref xml:method
     * @brief Formats XML data as a String
     * @param indent -1 for a single line (and no declaration), >= 0 for indents and newlines
     * @return the content
     * @see XML.toString
     */
    fun format(indent: Int): String? {
        try {
            // entities = doctype.getEntities()
            var useIndentAmount = false
            val factory = TransformerFactory.newInstance()
            if (indent != -1) {
                try {
                    factory.setAttribute("indent-number", indent)
                } catch (e: IllegalArgumentException) {
                    useIndentAmount = true
                }
            }
            val transformer = factory.newTransformer()

            // Add the XML declaration at the top if this node is the root and we're
            // not writing to a single line (indent = -1 means single line).
            if (indent == -1 || parent == null) {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            } else {
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            }

//      transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "sample.dtd");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml")

//      transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "yes");  // huh?

//      transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
//          "-//W3C//DTD XHTML 1.0 Transitional//EN");
//      transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
//          "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd");

            // For Android, because (at least 2.3.3) doesn't like indent-number
            if (useIndentAmount) {
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", indent.toString())
            }

//      transformer.setOutputProperty(OutputKeys.ENCODING,"ISO-8859-1");
//      transformer.setOutputProperty(OutputKeys.ENCODING,"UTF8");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            //      transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS

            // Always indent, otherwise the XML declaration will just be jammed
            // onto the first line with the XML code as well.
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")

//      Properties p = transformer.getOutputProperties();
//      for (Object key : p.keySet()) {
//        System.out.println(key + " -> " + p.get(key));
//      }

            // If you smell something, that's because this code stinks. No matter
            // the settings of the Transformer object, if the XML document already
            // has whitespace elements, it won't bother re-indenting/re-formatting.
            // So instead, transform the data once into a single line string.
            // If indent is -1, then we're done. Otherwise re-run and the settings
            // of the factory will kick in. If you know a better way to do this,
            // please contribute. I've wasted too much of my Sunday on it. But at
            // least the Giants are getting blown out by the Falcons.
            val decl = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            val sep = System.getProperty("line.separator")
            val tempWriter = StringWriter()
            val tempResult = StreamResult(tempWriter)
            transformer.transform(DOMSource(node), tempResult)
            var tempLines = PApplet.split(tempWriter.toString(), sep)
            //      PApplet.println(tempLines);
            if (tempLines[0].startsWith("<?xml")) {
                // Remove XML declaration from the top before slamming into one line
                val declEnd = tempLines[0].indexOf("?>") + 2
                //if (tempLines[0].length() == decl.length()) {
                if (tempLines[0].length == declEnd) {
                    // If it's all the XML declaration, remove it
//          PApplet.println("removing first line");
                    tempLines = PApplet.subset(tempLines, 1)
                } else {
//          PApplet.println("removing part of first line");
                    // If the first node has been moved to this line, be more careful
                    //tempLines[0] = tempLines[0].substring(decl.length());
                    tempLines[0] = tempLines[0].substring(declEnd)
                }
            }
            val singleLine = PApplet.join(PApplet.trim(tempLines), "")
            if (indent == -1) {
                return singleLine
            }

            // Might just be whitespace, which won't be valid XML for parsing below.
            // https://github.com/processing/processing/issues/1796
            // Since indent is not -1, that means they want valid XML,
            // so we'll give them the single line plus the decl... Lame? sure.
            if (singleLine.trim { it <= ' ' }.isEmpty()) {
                // You want whitespace? I've got your whitespace right here.
                return decl + sep + singleLine
            }

            // Since the indent is not -1, bring back the XML declaration
            //transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            val stringWriter = StringWriter()
            val xmlOutput = StreamResult(stringWriter)
            //      DOMSource source = new DOMSource(node);
            val source: Source = StreamSource(StringReader(singleLine))
            transformer.transform(source, xmlOutput)
            val outgoing = stringWriter.toString()

            // Add the XML declaration to the top if it's not there already
            return if (outgoing.startsWith(decl)) {
                val declen = decl.length
                val seplen = sep.length
                if (outgoing.length > declen + seplen &&
                        outgoing.substring(declen, declen + seplen) != sep) {
                    // make sure there's a line break between the XML decl and the code
                    outgoing.substring(0, decl.length) +
                            sep + outgoing.substring(decl.length)
                } else outgoing
            } else {
                decl + sep + outgoing
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun print() {
        PApplet.println(format(2))
    }

    /**
     * Return the XML document formatted with two spaces for indents.
     * Chosen to do this since it's the most common case (e.g. with println()).
     * Same as format(2). Use the format() function for more options.
     *
     * @webref xml:method
     * @brief Gets XML data as a String using default formatting
     * @return the content
     * @see XML.format
     */
    override fun toString(): String {
        //return format(2);
        return format(-1)!!
    }

    companion object {
        /**
         * @webref xml:method
         * @brief Converts String content to an XML object
         * @param data the content to be parsed as XML
         * @return an XML object, or null
         * @throws SAXException
         * @throws ParserConfigurationException
         * @throws IOException
         * @nowebref
         */
        @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
        fun parse(data: String?): XML {
            return parse(data, null)
        }

        /**
         * @nowebref
         */
        @JvmStatic
        @Throws(IOException::class, ParserConfigurationException::class, SAXException::class)
        fun parse(data: String?, options: String?): XML {
            return XML(StringReader(data), null)
        }
    }
}