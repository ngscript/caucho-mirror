/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.jsp;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.jsp.tagext.*;

import com.caucho.util.*;
import com.caucho.log.Log;
import com.caucho.vfs.*;
import com.caucho.xml.QName;
import com.caucho.xml.XmlChar;
import com.caucho.java.LineMap;
import com.caucho.server.http.*;

import com.caucho.jsp.java.JspNode;

/**
 * Parses the JSP page.  Both the XML and JSP tags are understood.  However,
 * escaping is always done using JSP rules.
 */
public class JspParser {
  static L10N L = new L10N(JspParser.class);
  static final Logger log = Log.open(JspParser.class);
  
  public static final String JSP_NS = "http://java.sun.com/JSP/Page";
  public static final String JSTL_CORE_URI = "http://java.sun.com/jsp/jstl/core";
  public static final String JSTL_FMT_URI = "http://java.sun.com/jsp/jstl/fmt";

  public static final QName PREFIX = new QName("prefix");
  public static final QName TAGLIB = new QName("taglib");
  public static final QName TAGDIR = new QName("tagdir");
  public static final QName URI = new QName("uri");
  
  public static final QName JSP_DECLARATION =
    new QName("jsp", "declaration", JSP_NS);
  
  public static final QName JSP_SCRIPTLET =
    new QName("jsp", "scriptlet", JSP_NS);
  
  public static final QName JSP_EXPRESSION =
    new QName("jsp", "expression", JSP_NS);
  
  public static final QName JSP_DIRECTIVE_PAGE =
    new QName("jsp", "directive.page", JSP_NS);
  
  public static final QName JSP_DIRECTIVE_INCLUDE =
    new QName("jsp", "directive.include", JSP_NS);
  
  public static final QName JSP_DIRECTIVE_CACHE =
    new QName("jsp", "directive.cache", JSP_NS);
  
  public static final QName JSP_DIRECTIVE_TAGLIB =
    new QName("jsp", "directive.taglib", JSP_NS);
  
  public static final QName JSP_DIRECTIVE_ATTRIBUTE =
    new QName("jsp", "directive.attribute", JSP_NS);
  
  public static final QName JSP_DIRECTIVE_VARIABLE =
    new QName("jsp", "directive.variable", JSP_NS);
  
  public static final QName JSP_DIRECTIVE_TAG =
    new QName("jsp", "directive.tag", JSP_NS);
  
  public static final QName JSTL_CORE_OUT =
    new QName("resin-c", "out", "urn:jsptld:" + JSTL_CORE_URI);
  
  public static final QName JSTL_CORE_CHOOSE =
    new QName("resin-c", "choose", "urn:jsptld:" + JSTL_CORE_URI);
  
  public static final QName JSTL_CORE_WHEN =
    new QName("resin-c", "when", "urn:jsptld:" + JSTL_CORE_URI);
  
  public static final QName JSTL_CORE_OTHERWISE =
    new QName("resin-c", "otherwise", "urn:jsptld:" + JSTL_CORE_URI);
  
  public static final QName JSTL_CORE_FOREACH =
    new QName("resin-c", "forEach", "urn:jsptld:" + JSTL_CORE_URI);

  private static final int TAG_UNKNOWN = 0;
  private static final int TAG_JSP = 1;
  private static final int TAG_RAW = 2;
  
  private ParseState _parseState;
  private JspBuilder _jspBuilder;
  private ParseTagManager _tagManager;

  private LineMap _lineMap;

  private ArrayList<String> _preludeList = new ArrayList<String>();
  private ArrayList<String> _codaList = new ArrayList<String>();
  
  private ArrayList<Include> _includes = new ArrayList<Include>();
  
  private Path _jspPath;
  private ReadStream _stream;
  private String _uriPwd;

  private String _contextPath = "";
  private String _filename = "";
  private int _line;
  private int _lineStart;
  
  private int _charCount;
  private int _startText;

  private int _peek = -1;
  private boolean _seenCr = false;

  private Namespace _namespaces;
  
  private boolean _isXml;
  private boolean _isTop = true;
  
  private CharBuffer _tag = new CharBuffer();
  private CharBuffer _value = new CharBuffer();

  private CharBuffer _text = new CharBuffer();

  /**
   * Sets the JSP builder, which receives the SAX-like events from
   * JSP parser.
   */
  void setJspBuilder(JspBuilder builder)
  {
    _jspBuilder = builder;
  }

  /**
   * Sets the context path for error messages.
   */
  void setContextPath(String contextPath)
  {
    _contextPath = contextPath;
  }

  /**
   * Sets the parse state, which stores state information for the parsing.
   */
  void setParseState(ParseState parseState)
  {
    _parseState = parseState;
  }

  /**
   * Sets the parse state, which stores state information for the parsing.
   */
  ParseState getParseState()
  {
    return _parseState;
  }

  /**
   * Sets the tag manager
   */
  void setTagManager(ParseTagManager manager)
  {
    _tagManager = manager;
  }

  /**
   * Returns true if JSP EL expressions are enabled.
   */
  private boolean isELIgnored()
  {
    return _parseState.isELIgnored();
  }

  /**
   * Adds a prelude.
   */
  public void addPrelude(String prelude)
  {
    _preludeList.add(prelude);
  }

  /**
   * Adds a coda.
   */
  public void addCoda(String coda)
  {
    _codaList.add(coda);
  }

  /**
   * Starts parsing the JSP page.
   *
   * @param path the JSP source file
   * @param uri the URI for the JSP source file.
   */
  void parse(Path path, String uri)
    throws Exception
  {
    _namespaces = new Namespace(null, "jsp", JSP_NS);
    _parseState.pushNamespace("jsp", JSP_NS);

    _filename = _contextPath + uri;
 
    if (uri != null) {
      int p = uri.lastIndexOf('/');
      _uriPwd = p <= 0 ? "/" : uri.substring(0, p + 1);
    }
    else {
      _uriPwd = "/";
    }
    _parseState.setUriPwd(_uriPwd);

    ReadStream is = path.openRead();
    path.setUserPath(uri);

    try {
      parseJsp(is);
    } finally {
      is.close();
      for (int i = 0; i < _includes.size(); i++) {
        Include inc = _includes.get(i);
        inc._stream.close();
      }
    }
  }

  /**
   * Starts parsing the JSP page as a tag.
   *
   * @param path the JSP source file
   * @param uri the URI for the JSP source file.
   */
  void parseTag(Path path, String uri)
    throws Exception
  {
    _parseState.setTag(true);
    
    parse(path, uri);
  }

  /**
   * Top-level JSP parser.
   *
   * @param stream the read stream containing the JSP file
   *
   * @return an XML DOM containing the JSP.
   */
  private void parseJsp(ReadStream stream)
    throws Exception
  {
    _text.clear();
    _includes.clear();

    String uriPwd = _uriPwd;
    
    for (int i = _codaList.size() - 1; i >= 0; i--)
      pushInclude(_codaList.get(i));
    
    addInclude(stream, uriPwd);
    
    for (int i = _preludeList.size() - 1; i >= 0; i--)
      pushInclude(_preludeList.get(i));

    setLocation();
    _jspBuilder.startDocument();

    String pageEncoding = _parseState.getPageEncoding();

    int ch;

    if (pageEncoding != null) {
      stream.setEncoding(pageEncoding);

      ch = read();
    }
    else if ((ch = read()) != 0xef) {
    }
    else if ((ch = read()) != 0xbb) {
      _peek = 0xbb;
      ch = 0xef;
    }
    else if ((ch = read()) != 0xbf) {
      throw error(L.l("Expected 0xbf in UTF-8 header.  UTF-8 pages with the initial byte 0xbb expect 0xbf immediately following.  The 0xbb 0xbf sequence is used by some application to suggest UTF-8 encoding without a directive."));
    }
    else {
      stream.setEncoding("UTF-8");
      ch = read();
    }

    ch = parseXmlDeclaration(ch);
    
    try {
      parseNode(ch);
    } finally {
      for (int i = 0; i < _includes.size(); i++) {
	Include inc = _includes.get(i);
	inc._stream.close();
      }
    }

    setLocation();
    _jspBuilder.endDocument();
  }

  private int parseXmlDeclaration(int ch)
    throws IOException, JspParseException
  {
    if (ch != '<')
      return ch;
    else if ((ch = read()) != '?') {
      unread(ch);
      return '<';
    }
    else if ((ch = read()) != 'x') {
      addText("<?");
      return ch;
    }
    else if ((ch = read()) != 'm') {
      addText("<?x");
      return ch;
    }
    else if ((ch = read()) != 'l') {
      addText("<?xm");
      return ch;
    }
    else if (! XmlChar.isWhitespace((ch = read()))) {
      addText("<?xml");
      return ch;
    }

    addText("<?xml ");
    ch = skipWhitespace(ch);
    while (XmlChar.isNameStart(ch)) {
      ch = readName(ch);
      String name = _tag.toString();

      addText(name);
      if (XmlChar.isWhitespace(ch))
        addText(' ');

      ch = skipWhitespace(ch);
      if (ch != '=')
        return ch;

      readValue(name, ch, true);
      String value = _value.toString();

      addText("=\"");
      addText(value);
      addText("\"");

      ch = read();
      if (XmlChar.isWhitespace(ch))
        addText(' ');
      ch = skipWhitespace(ch);
    }

    if (ch != '?')
      return ch;
    else if ((ch = read()) != '>') {
      addText('?');
      return ch;
    }
    else {
      addText("?>");
      return read();
    }
  }
    
  private void parseNode(int ch)
    throws IOException, JspParseException
  {
    while (ch != -1) {
      switch (ch) {
      case '<':
	{
	  switch ((ch = read())) {
	  case '%':
	    if (_isXml)
	      throw error(L.l("'<%' syntax is not allowed in JSP/XML syntax."));
	    
	    parseScriptlet();
	    _startText = _charCount;

            // escape '\\' after scriptlet at end of line
	    if ((ch = read()) == '\\') {
              if ((ch = read()) == '\n') {
                ch = read();
              }
              else if (ch == '\r') {
                if ((ch = read()) == '\n')
                  ch = read();
              }
              else
                addText('\\');
            }
	    break;

	  case '/':
	    ch = parseCloseTag();
	    break;

	  case '\\':
	    if ((ch = read()) == '%') {
	      addText("<%");
	      ch = read();
	    }
            else
	      addText("<\\");
	    break;

          case '!':
            if (! _isXml)
              addText("<!");
            else if ((ch = read()) == '[')
              parseCdata();
            else if (ch == '-' && (ch = read()) == '-')
              parseXmlComment();
            else
              throw error(L.l("`{0}' was not expected after `<!'.  In the XML syntax, only <!-- ... --> and <![CDATA[ ... ]> are legal.  You can use `&amp;!' to escape `<!'.",
                              badChar(ch)));

            ch = read();
            break;

	  default:
	    if (! XmlChar.isNameStart(ch)) {
	      addText('<');
	      break;
	    }

	    ch = readName(ch);
	    String name = _tag.toString();
            int tagCode = getTag(name);
	    if (! _isXml && tagCode == TAG_UNKNOWN) {
	      addText("<");
	      addText(name);
	      break;
	    }

	    if (_isTop && name.equals("jsp:root")) {
              _text.clear();
              _isXml = true;
	      _parseState.setELIgnoredDefault(false);
            }
            _isTop = false;
	    parseOpenTag(name, ch, tagCode == TAG_UNKNOWN);

            ch = read();
            
            // escape '\\' after scriptlet at end of line
	    if (! _isXml && ch == '\\') {
              if ((ch = read()) == '\n') {
                ch = read();
              }
              else if (ch == '\r') {
                if ((ch = read()) == '\n')
                  ch = read();
              }
            }
	  }
	  break;
	}

      case '&':
        if (! _isXml)
          addText((char) ch);
        else {
          addText((char) parseEntity());
	}
        ch = read();
        break;

      case '$':
        ch = read();

        if (ch == '{' && ! isELIgnored())
          ch = parseJspExpression();
        else
          addText('$');
        break;

      case '#':
        ch = read();

        if (ch == '{' && ! isELIgnored()) {
	  // XXX: error
          ch = parseJspExpression();
	}
        else
          addText('#');
        break;

      case '\\':
        switch (ch = read()) {
        case '$':
          if (! isELIgnored()) {
            addText('$');
            ch = read();
          }
          else
            addText('\\');
          break;
	  
        case '#':
          if (! isELIgnored()) {
            addText('#');
            ch = read();
          }
          else
            addText('\\');
          break;

        case '\\':
          addText('\\');
          break;

        default:
          addText('\\');
          break;
        }
        break;

      default:
	addText((char) ch);
	ch = read();
        break;
      }
    }
    
    addText();

    /* XXX: end document
    if (! _activeNode.getNodeName().equals("jsp:root"))
      throw error(L.l("`</{0}>' expected at end of file.  For XML, the top-level tag must have a matching closing tag.",
                      activeNode.getNodeName()));
    */
  }

  /**
   * JSTL-style expressions.  Currently understood:
   *
   * <code><pre>
   * ${a * b} - any arbitrary expression
   * </pre></code>
   */
  private int parseJspExpression()
    throws IOException, JspParseException
  {
    addText();

    String filename = _filename;
    int line = _line;

    CharBuffer cb = CharBuffer.allocate();
    int ch;
    cb.append("${");
    for (ch = read(); ch >= 0 && ch != '}'; ch = read())
      cb.append((char) ch);
    cb.append("}");

    ch = read();

    processTaglib("resin-c", JSTL_CORE_URI);
    
    setLocation(filename, line);
    _jspBuilder.startElement(JSTL_CORE_OUT);
    _jspBuilder.attribute(new QName("value"), cb.close());
    _jspBuilder.attribute(new QName("escapeXml"), "false");
    _jspBuilder.endAttributes();
    _jspBuilder.endElement(JSTL_CORE_OUT.getName());

    return ch;
  }

  /**
   * Parses a &lt;![CDATA[ block.  All text in the CDATA is uninterpreted.
   */
  private void parseCdata()
    throws IOException, JspParseException
  {
    int ch;

    ch = readName(read());

    String name = _tag.toString();

    if (! name.equals("CDATA"))
      throw error(L.l("Expected <![CDATA[ at <![`{0}'.", name,
                      "XML only recognizes the <![CDATA directive."));

    if (ch != '[')
      throw error(L.l("Expected `[' at `{0}'.  The XML CDATA syntax is <![CDATA[...]]>.",
                      String.valueOf(ch)));

    String filename = _filename;
    int line = _line;
                  
    while ((ch = read()) >= 0) {
      while (ch == ']') {
        if ((ch = read()) != ']')
          addText(']');
        else if ((ch = read()) != '>')
          addText("]]");
        else
          return;
      }

      addText((char) ch);
    }

    throw error(L.l("Expected closing ]]> at end of file to match <![[CDATA at {0}.", filename + ":" + line));
  }

  /**
   * Parses an XML name for elements and attribute names.  The parsed name
   * is stored in the 'tag' class variable.
   *
   * @param ch the next character
   *
   * @return the character following the name
   */
  private int readName(int ch)
    throws IOException, JspParseException
  {
    _tag.clear();

    for (; XmlChar.isNameChar((char) ch); ch = read())
      _tag.append((char) ch);

    return ch;
  }

  private void parsePageDirective(String name, String value)
    throws IOException, JspParseException
  {
    if ("isELIgnored".equals(name)) {
      if ("true".equals(value))
        _parseState.setELIgnored(true);
    }
  }

  /**
   * Parses a special JSP syntax.
   */
  private void parseScriptlet()
    throws IOException, JspParseException
  {
    addText();

    _lineStart = _line;

    int ch = read();

    // probably should be a qname
    QName eltName = null;

    switch (ch) {
    case '=':
      eltName = JSP_EXPRESSION;
      ch = read();
      break;

    case '!':
      eltName = JSP_DECLARATION;
      ch = read();
      break;

    case '@':
      parseDirective();
      return;

    case '-':
      if ((ch = read()) == '-') {
	parseComment();
	return;
      }
      else {
        eltName = JSP_SCRIPTLET;
	addText('-');
      }
      break;

    default:
      eltName = JSP_SCRIPTLET;
      break;
    }

    setLocation(_filename, _lineStart);
    _jspBuilder.startElement(eltName);
    _jspBuilder.endAttributes();

    while (ch >= 0) {
      switch (ch) {
      case '\\':
	addText('\\');
	ch = read();
	if (ch >= 0)
	  addText((char) ch);
	ch = read();
	break;

      case '%':
	ch = read();
	if (ch == '>') {
          createText();
          setLocation();
          _jspBuilder.endElement(eltName.getName());
	  return;
	}
	else if (ch == '\\') {
	  ch = read();
	  if (ch == '>') {
	    addText("%");
	  }
	  else
	    addText("%\\");
	}
	else
	  addText('%');
	break;

      default:
	addText((char) ch);
	ch = read();
	break;
      }
    }

    createText();    
    setLocation();
    _jspBuilder.endElement(eltName.getName());
  }

  /**
   * Parses the JSP directive syntax.
   */
  private void parseDirective()
    throws IOException, JspParseException
  {
    String language = null;

    int ch = skipWhitespace(read());
    String directive = "";
    if (XmlChar.isNameStart(ch)) {
      ch = readName(ch);
      directive = _tag.toString();
    }
    else
      throw error(L.l("Expected jsp directive name at `{0}'.  JSP directive syntax is <%@ name attr1='value1' ... %>",
                      badChar(ch)));

    QName qname;

    if (directive.equals("page"))
      qname = JSP_DIRECTIVE_PAGE;
    else if (directive.equals("include"))
      qname = JSP_DIRECTIVE_INCLUDE;
    else if (directive.equals("taglib"))
      qname = JSP_DIRECTIVE_TAGLIB;
    else if (directive.equals("cache"))
      qname = JSP_DIRECTIVE_CACHE;
    else if (directive.equals("attribute"))
      qname = JSP_DIRECTIVE_ATTRIBUTE;
    else if (directive.equals("variable"))
      qname = JSP_DIRECTIVE_VARIABLE;
    else if (directive.equals("tag"))
      qname = JSP_DIRECTIVE_TAG;
    else
      throw error(L.l("`{0}' is an unknown jsp directive.  Only <%@ page ... %>, <%@ include ... %>, <%@ taglib ... %>, and <%@ cache ... %> are known.", directive));

    unread(ch);
    
    ArrayList<QName> keys = new ArrayList<QName>();
    ArrayList<String> values = new ArrayList<String>();

    parseAttributes(keys, values);

    ch = skipWhitespace(read());

    if (ch != '%' || (ch = read()) != '>') {
      throw error(L.l("expected `%>' at {0}.  JSP directive syntax is `<%@ name attr1='value1' ... %>'.  (Started at line {1})",
                      badChar(ch), _lineStart));
    }

    setLocation(_filename, _lineStart);
    _lineStart = _line;
    _jspBuilder.startElement(qname);

    for (int i = 0; i < keys.size(); i++) {
      _jspBuilder.attribute(keys.get(i), values.get(i));
    }
    _jspBuilder.endAttributes();
  
    if (qname.equals(JSP_DIRECTIVE_TAGLIB))
      processTaglibDirective(keys, values);

    setLocation();
    _jspBuilder.endElement(qname.getName());

    if (qname.equals(JSP_DIRECTIVE_PAGE)) {
      String contentEncoding = _parseState.getPageEncoding();
      if (contentEncoding == null)
        contentEncoding = _parseState.getCharEncoding();

      if (contentEncoding != null) {
        try {
          _stream.setEncoding(contentEncoding);
        } catch (Exception e) {
	  log.log(Level.FINER, e.toString(), e);
	  
          throw error(L.l("unknown content encoding `{0}'", contentEncoding),
		      e);
        }
      }
    }
    /*
    if (directive.equals("include"))
      parseIncludeDirective(elt);
    else if (directive.equals("taglib"))
      parseTaglibDirective(elt);
    */
  }

  /**
   * Parses an XML comment.
   */
  private void parseComment()
    throws IOException, JspParseException
  {
    int ch = read();

    while (ch >= 0) {
      if (ch == '-') {
	ch = read();
	while (ch == '-') {
	  if ((ch = read()) == '-')
	    continue;
	  else if (ch == '%' && (ch = read()) == '>')
	    return;
	  else if (ch == '-')
	    ch = read();
	}
      }
      else
	ch = read();
    }
  }
  
  private void parseXmlComment()
    throws IOException, JspParseException
  {
    int ch;
    
    while ((ch = read()) >= 0) {
      while (ch == '-') {
        if ((ch = read()) == '-' && (ch = read()) == '>')
          return;
      }
    }
  }

  /**
   * Parses the open tag.
   */
  private void parseOpenTag(String name, int ch, boolean isXml)
    throws IOException, JspParseException
  {
    addText();

    ch = skipWhitespace(ch);

    ArrayList<QName> keys = new ArrayList<QName>();
    ArrayList<String> values = new ArrayList<String>();

    unread(ch);

    parseAttributes(keys, values);

    QName qname = getElementQName(name);

    setLocation(_filename, _lineStart);
    _lineStart = _line;

    _jspBuilder.startElement(qname);

    for (int i = 0; i < keys.size(); i++) {
      QName key = keys.get(i);
      String value = values.get(i);
      
      _jspBuilder.attribute(key, value);
    }

    _jspBuilder.endAttributes();
    
    if (qname.equals(JSP_DIRECTIVE_TAGLIB))
      processTaglibDirective(keys, values);
    
    ch = skipWhitespace(read());

    JspNode node = _jspBuilder.getCurrentNode();

    if (ch == '/') {
      if ((ch = read()) != '>')
	throw error(L.l("expected `/>' at `{0}' (for tag `<{1}>' at line {2}).  The XML empty tag syntax is: <tag attr1='value1'/>",
                        badChar(ch), name, String.valueOf(_lineStart)));
      
      setLocation();
      _jspBuilder.endElement(qname.getName());
    }
    else if (ch != '>')
      throw error(L.l("expected `>' at `{0}' (for tag `<{1}>' at line {2}).  The XML tag syntax is: <tag attr1='value1'>",
                      badChar(ch), name, String.valueOf(_lineStart)));
    // If tagdependent and not XML mode, then read the raw text.
    else if ("tagdependent".equals(node.getBodyContent()) && ! _isXml) {
      String tail = "</" + name + ">";
      for (ch = read(); ch >= 0; ch = read()) {
	_text.append((char) ch);
	if (_text.endsWith(tail)) {
	  _text.setLength(_text.length() - tail.length());
	  addText();
          _jspBuilder.endElement(qname.getName());
	  return;
        }
      }
      throw error(L.l("expected `{0}' at end of file (for tag <{1}> at line {2}).  Tags with `tagdependent' content need close tags.",
                      tail, String.valueOf(_lineStart)));
    }
  }

  /**
   * Returns the full QName for the JSP page's name.
   */
  private QName getElementQName(String name)
  {
    int p = name.lastIndexOf(':');

    if (p > 0) {
      String prefix = name.substring(0, p);
      String url = Namespace.find(_namespaces, prefix);

      if (url != null)
        return new QName(prefix, name.substring(p + 1), url);
      else
        return new QName("", name, "");
    }
    else {
      String url = Namespace.find(_namespaces, "");

      if (url != null)
        return new QName("", name, url);
      else
        return new QName("", name, "");
    }
  }

  /**
   * Returns the full QName for the JSP page's name.
   */
  private QName getAttributeQName(String name)
  {
    int p = name.lastIndexOf(':');

    if (p > 0) {
      String prefix = name.substring(0, p);
      String url = Namespace.find(_namespaces, prefix);

      if (url != null)
        return new QName(prefix, name.substring(p + 1), url);
      else
        return new QName("", name, "");
    }
    else
      return new QName("", name, "");
  }

  /**
   * Parses the attributes of an element.
   */
  private void parseAttributes(ArrayList<QName> names,
                               ArrayList<String> values)
    throws IOException, JspParseException
  {
    names.clear();
    values.clear();

    int ch = skipWhitespace(read());

    while (XmlChar.isNameStart(ch)) {
      ch = readName(ch);
      String key = _tag.toString();

      readValue(key, ch, _isXml);
      String value = _value.toString(); 

      if (key.startsWith("xmlns:")) {
        String prefix = key.substring(6);

	_jspBuilder.startPrefixMapping(prefix, value);
	//_parseState.pushNamespace(prefix, value);
        _namespaces = new Namespace(_namespaces, prefix, value);
      }
      else if (key.equals("xmlns")) {
	_jspBuilder.startPrefixMapping("", value);
	//_parseState.pushNamespace(prefix, value);
	//_parseState.pushNamespace("", value);
        _namespaces = new Namespace(_namespaces, "", value);
      }
      else {
        names.add(getAttributeQName(key));
        values.add(value);
      }
      
      ch = skipWhitespace(read());
    }

    unread(ch);
  }
  
  /**
   * Reads an attribute value.
   */
  private void readValue(String attribute, int ch, boolean isXml)
    throws IOException, JspParseException
  {
    boolean isRuntimeAttribute = false;
    
    _value.clear();

    ch = skipWhitespace(ch);

    if (ch != '=') {
      unread(ch);
      return;
    }

    ch = skipWhitespace(read());

    if (ch != '\'' && ch != '"') {
      if (XmlChar.isNameChar(ch)) {
        ch = readName(ch);

        throw error(L.l("`{0}' attribute value must be quoted at `{1}'.  JSP attribute syntax is either attr=\"value\" or attr='value'.",
                        attribute, _tag));
      }
      else
        throw error(L.l("`{0}' attribute value must be quoted at `{1}'.  JSP attribute syntax is either attr=\"value\" or attr='value'.",
                        attribute, badChar(ch)));
    }

    int end = ch;
    int lastCh = 0;

    ch = read();
    if (ch != '<') {
    }
    else if ((ch = read()) != '%')
      _value.append('<');
    else if ((ch = read()) != '=')
      _value.append("<%");
    else {
      _value.append("<%");
      isRuntimeAttribute = true;
    }

    while (ch != -1 && (ch != end || isRuntimeAttribute)) {
      if (ch == '\\') {
        switch ((ch = read())) {
        case '\\':
        case '\'':
        case '\"':
	  // jsp/1505 vs jsp/184s
	  // _value.append('\\');
          _value.append((char) ch);
          ch = read();
          break;

        case '>':
          if (lastCh == '%') {
            _value.append('>');
            ch = read();
          }
          else
            _value.append('\\');
          break;

        case '%':
          if (lastCh == '<') {
            _value.append('%');
            ch = read();
          }
          else
            _value.append('\\');
          break;

        default:
          _value.append('\\');
          break;
        }
      }
      else if (ch == '%' && isRuntimeAttribute) {
        _value.append('%');
        ch = read();
        if (ch == '>')
          isRuntimeAttribute = false;
      }
      else if (ch == '&' && isXml) {
        lastCh = -1;
        _value.append((char) parseEntity());
        ch = read();
      }
      else if (ch == '&') {
        if ((ch = read()) == 'a') {
          if ((ch = read()) != 'p')
            _value.append("&a");
          else if ((ch = read()) != 'o')
            _value.append("&ap");
          else if ((ch = read()) != 's')
            _value.append("&apo");
          else if ((ch = read()) != ';')
            _value.append("&apos");
          else {
            _value.append('\'');
            ch = read();
          }
        }
        else if (ch == 'q') {
          if ((ch = read()) != 'u')
            _value.append("&q");
          else if ((ch = read()) != 'o')
            _value.append("&qu");
          else if ((ch = read()) != 't')
            _value.append("&quo");
          else if ((ch = read()) != ';')
            _value.append("&quot");
          else {
            _value.append('"');
            ch = read();
          }
        }
        else
          _value.append('&');
      }
      else {
        _value.append((char) ch);
        lastCh = ch;
        ch = read();
      }
    }
  }

  /**
   * Parses an XML entity.
   */
  int parseEntity()
    throws IOException, JspParseException
  {
    int ch = read();

    if (_isXml && ch == '#') {
      int value = 0;

      for (ch = read(); ch >= '0' && ch <= '9'; ch = read())
        value = 10 * value + ch - '0';

      if (ch != ';')
        throw error(L.l("expected ';' at `{0}' in character entity.  The XML character entities syntax is &#nn;",
                        badChar(ch)));

      return (char) value;
    }

    CharBuffer cb = CharBuffer.allocate();
    for (; ch >= 'a' && ch <= 'z'; ch = read())
      cb.append((char) ch);

    if (ch != ';') {
      
      log.warning(L.l("expected ';' at `{0}' in entity `&{1}'.  The XML entity syntax is &name;",
                      badChar(ch), cb));
    }

    String entity = cb.close();
    if (entity.equals("lt"))
      return '<';
    else if (entity.equals("gt"))
      return '>';
    else if (entity.equals("amp"))
      return '&';
    else if (entity.equals("apos"))
      return '\'';
    else if (entity.equals("quot"))
      return '"';
    else
      throw error(L.l("unknown entity `&{0};'.  XML only recognizes the special entities &lt;, &gt;, &amp;, &apos; and &quot;", entity));
  }

  private int parseCloseTag()
    throws IOException, JspParseException
  {
    int ch;

    if (! XmlChar.isNameStart(ch = read())) {
      addText("</");
      return ch;
    }
    
    ch = readName(ch);
    String name = _tag.toString();
    if (! _isXml && getTag(name) == TAG_UNKNOWN) {
      addText("</");
      addText(name);
      return ch;
    }

    ch = skipWhitespace(ch);
    if (ch != '>')
      throw error(L.l("expected `>' at {0}.  The XML close tag syntax is </name>.", badChar(ch)));

    JspNode node = _jspBuilder.getCurrentNode();
    String nodeName = node.getTagName();
    if (nodeName.equals(name)) {
    }
    else if (nodeName.equals("resin-c:when")) {
      throw error(L.l("#if expects closing #end before </{0}> (#if at {1}).  #if statements require #end before the enclosing tag closes.",
                      name, String.valueOf(node.getStartLine())));
    }
    else if (nodeName.equals("resin-c:otherwise")) {
      throw error(L.l("#else expects closing #end before </{0}> (#else at {1}).  #if statements require #end before the enclosing tag closes.",
                      name, String.valueOf(node.getStartLine())));
    }
    else {
      throw error(L.l("expected </{0}> at </{1}>.  Closing tags must match opened tags.",
                      nodeName, name));
    }

    addText();

    setLocation();
    _jspBuilder.endElement(name);

    return read();
  }

  private void processTaglibDirective(ArrayList<QName> keys,
                                      ArrayList<String> values)
    throws IOException, JspParseException
  {
    int p = keys.indexOf(PREFIX);
    if (p < 0)
      throw error(L.l("The taglib directive requires a `prefix' attribute.  `prefix' is the XML prefix for all tags in the taglib."));
    String prefix = values.get(p);

    String uri = null;
    p = keys.indexOf(URI);
    if (p >= 0)
      uri = values.get(p);
    
    String tagdir = null;
    p = keys.indexOf(TAGDIR);
    if (p >= 0)
      tagdir = values.get(p);
    
    if (uri != null)
      processTaglib(prefix, uri);
    else if (tagdir != null)
      processTaglibDir(prefix, tagdir);
  }

  /**
   * Adds a new known taglib prefix to the current namespace.
   */
  private void processTaglib(String prefix, String uri)
    throws JspParseException
  {
    Taglib taglib = null;
    
    int colon = uri.indexOf(':');
    int slash = uri.indexOf('/');

    String location = null;

    if (colon > 0 && colon < slash)
      location = uri;
    else if (slash == 0)
      location = uri;
    else
      location = _uriPwd + uri;

    try {
      taglib = _tagManager.addTaglib(prefix, uri, location);
      String tldURI = "urn:jsptld:" + uri;

      _parseState.pushNamespace(prefix, tldURI);
      _namespaces = new Namespace(_namespaces, prefix, tldURI);
      return;
    } catch (JspParseException e) {
      throw error(e);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    if (colon > 0 && colon < slash)
      throw error(L.l("Unknown taglib `{0}'.  Taglibs specified with an absolute URI must either be:\n1) specified in the web.xml\n2) defined in a jar's .tld in META-INF\n3) defined in a .tld in WEB-INF\n4) predefined by Resin",
                      uri));
  }

  /**
   * Adds a new known tag dir to the current namespace.
   */
  private void processTaglibDir(String prefix, String tagDir)
    throws JspParseException
  {
    Taglib taglib = null;
    
    try {
      taglib = _tagManager.addTaglibDir(prefix, tagDir);
      String tagURI = "urn:jsptagdir:" + tagDir;
      _parseState.pushNamespace(prefix, tagURI);
      _namespaces = new Namespace(_namespaces, prefix, tagURI);
      return;
    } catch (JspParseException e) {
      throw error(e);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  private void processIncludeDirective(ArrayList keys, ArrayList values)
    throws IOException, JspParseException
  {
    int p = keys.indexOf("file");
    if (p < 0)
      throw error(L.l("The include directive requires a `file' attribute."));
    String file = (String) values.get(p);

    pushInclude(file);
  }

  public void pushInclude(String value)
    throws IOException, JspParseException
  {
    if (value.equals(""))
      throw error("include directive needs `file' attribute. Use either <%@ include file='myfile.jsp' %> or <jsp:directive.include file='myfile.jsp'/>");

    Path include;
    if (value.length() > 0 && value.charAt(0) == '/')
      include = _parseState.resolvePath(value);
    else
      include = _parseState.resolvePath(_uriPwd + value);

    String newUrl = _uriPwd;

    if (value.startsWith("/"))
      newUrl = value;
    else
      newUrl = _uriPwd + value;
    
    include.setUserPath(newUrl);

    String newUrlPwd;
    int p = newUrl.lastIndexOf('/');
    newUrlPwd = newUrl.substring(0, p + 1);
    
    if (_jspPath != null && _jspPath.equals(include))
      throw error(L.l("circular include of `{0}' forbidden.  A JSP file may not include itself.", include));
    for (int i = 0; i < _includes.size(); i++) {
      Include inc = _includes.get(i);
      if (inc._stream.getPath().equals(include))
	throw error(L.l("circular include of `{0}'.  A JSP file may not include itself.", include));
    }

    try {
      addInclude(include.openRead(), newUrlPwd);
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);

      if (include.exists())
        throw error(L.l("can't open include of `{0}'.  `{1}' exists but it's not readable.",
                        value, include.getNativePath()));
      else
        throw error(L.l("can't open include of `{0}'.  `{1}' does not exist.",
                        value, include.getNativePath()));
    }
  }

  private void addInclude(ReadStream stream, String newUrlPwd)
    throws IOException, JspParseException
  {
    addText();

    readLf();

    if (_stream != null)
      _includes.add(new Include(_stream, _line, _uriPwd));

    _parseState.addDepend(stream.getPath());

    try {
      String encoding = _stream.getEncoding();
      if (encoding != null)
	stream.setEncoding(encoding);
    } catch (Exception e) {
    }
    _stream = stream;

    _filename = stream.getUserPath();
    _jspPath = stream.getPath();
    _line = 1;
    _lineStart = _line;
    _uriPwd = newUrlPwd;
    _parseState.setUriPwd(_uriPwd);
  }

  /**
   * Skips whitespace characters.
   *
   * @param ch the current character
   *
   * @return the first non-whitespace character
   */
  private int skipWhitespace(int ch)
    throws IOException, JspParseException
  {
    for (; XmlChar.isWhitespace(ch); ch = read()) {
    }
    
    return ch;
  }

  /**
   * Skips whitespace to end of line
   *
   * @param ch the current character
   *
   * @return the first non-whitespace character
   */
  private int skipWhitespaceToEndOfLine(int ch)
    throws IOException, JspParseException
  {
    for (; XmlChar.isWhitespace(ch); ch = read()) {
      if (ch == '\n')
        return read();
      else if (ch == '\r') {
        ch = read();
        if (ch == '\n')
          return read();
        else
          return ch;
      }
    }
    
    return ch;
  }

  private void addText(char ch)
  {
    _text.append(ch);
  }

  private void addText(String s)
  {
    _text.append(s);
  }

  private void addText()
    throws JspParseException
  {
    if (_text.length() > 0)
      createText();
    
    _startText = _charCount;
    _lineStart = _line;
  }

  private void createText()
    throws JspParseException
  {
    String string = _text.toString();

    setLocation(_filename, _lineStart);

    if (_parseState.isTrimWhitespace() && isWhitespace(string)) {
    }
    else
      _jspBuilder.text(string, _filename, _lineStart, _line);
    
    _lineStart = _line;
    _text.clear();
    _startText = _charCount;
  }

  private boolean isWhitespace(String s)
  {
    int length = s.length();

    for (int i = 0; i < length; i++) {
      if (! Character.isWhitespace(s.charAt(i)))
	return false;
    }

    return true;
  }
  

  /**
   * Checks to see if the element name represents a tag.
   */
  private int getTag(String name)
    throws JspParseException
  {
    int p = name.indexOf(':');
    if (p < 0)
      return TAG_UNKNOWN;

    String prefix = name.substring(0, p);
    String local = name.substring(p + 1);

    String url = Namespace.find(_namespaces, prefix);

    if (url != null)
      return TAG_JSP;
    else
      return TAG_UNKNOWN;

    /*
    QName qname;

    if (url != null)
      qname = new QName(prefix, local, url);
    else
      qname = new QName(prefix, local, null);

    TagInfo tag = _tagManager.getTag(qname);

    if (tag != null)
      return TAG_JSP;
    else
      return TAG_UNKNOWN;
    */
  }

  private void unread(int ch)
  {
    _peek = ch;
  }
  
  /**
   * Reads the next character we're in the middle of cr/lf.
   */
  private void readLf() throws IOException, JspParseException
  {
    if (_seenCr) {
      int ch = read();

      if (ch != '\n')
	_peek = ch;
    }
  }
  
  /**
   * Reads the next character.
   */
  private int read() throws IOException, JspParseException
  {
    int ch;

    if (_peek >= 0) {
      ch = _peek;
      _peek = -1;
      return ch;
    }

    try {
      if ((ch = _stream.readChar()) >= 0) {
        _charCount++;
        
        if (ch == '\r') {
          _line++;
          _charCount = 0;
          _seenCr = true;
        }
        else if (ch == '\n' && _seenCr) {
          _seenCr = false;
          _charCount = 0;
	}
        else if (ch == '\n') {
          _line++;
          _charCount = 0;
        }
        else {
          _seenCr = false;
	}

        return ch;
      }
    } catch (IOException e) {
      throw error(e.toString());
    }

    _stream.close();
    _seenCr = false;
    
    if (_includes.size() > 0) {
      setLocation(_filename, _line);
      
      Include include = _includes.get(_includes.size() - 1);
      _includes.remove(_includes.size() - 1);

      _stream = include._stream;
      _filename = _stream.getUserPath();
      _jspPath = _stream.getPath();
      _line = include._line;
      _lineStart = _line;
      _uriPwd = include._uriPwd;
      _parseState.setUriPwd(_uriPwd);

      setLocation(_filename, _line);
      
      return read();
    }

    return -1;
  }

  void clear(Path appDir, String errorPage)
  {
  }

  /**
   * Creates an error message adding the filename and line.
   *
   * @param message the error message
   */
  public JspParseException error(Exception e)
  {
    String message = e.getMessage();

    if (e instanceof JspParseException) {
      log.log(Level.FINE, e.toString(), e);
    }

    if (e instanceof JspLineParseException)
      return (JspLineParseException) e;
    else if (e instanceof LineCompileException)
      return new JspLineParseException(e);

    if (_lineMap == null)
      return new JspLineParseException(_filename + ":" + _line + ": "  + message,
				       e);
    else {
      LineMap.Line line = _lineMap.getLine(_line);
      
      return new JspLineParseException(line.getSourceFilename() + ":" +
				       line.getSourceLine(_line) + ": "  +
				       message,
				       e);
    }
  }

  /**
   * Creates an error message adding the filename and line.
   *
   * @param message the error message
   */
  public JspParseException error(String message)
  {
    if (_lineMap == null)
      return new JspLineParseException(_filename + ":" + _line + ": "  + message);
    else {
      LineMap.Line line = _lineMap.getLine(_line);
      
      return new JspLineParseException(line.getSourceFilename() + ":" +
				       line.getSourceLine(_line) + ": "  +
				       message);
    }
  }

  /**
   * Creates an error message adding the filename and line.
   *
   * @param message the error message
   */
  public JspParseException error(String message, Throwable e)
  {
    if (_lineMap == null)
      return new JspLineParseException(_filename + ":" + _line + ": "  + message, e);
    else {
      LineMap.Line line = _lineMap.getLine(_line);
      
      return new JspLineParseException(line.getSourceFilename() + ":" +
				       line.getSourceLine(_line) + ": "  +
				       message,
				       e);
    }
  }

  /**
   * Sets the current location in the original file
   */
  private void setLocation()
  {
    setLocation(_filename, _line);
  }

  /**
   * Sets the current location in the original file
   *
   * @param filename the filename
   * @param line the line in the source file
   */
  private void setLocation(String filename, int line)
  {
    if (_lineMap == null) {
      _jspBuilder.setLocation(filename, line);
    }
    else {
      LineMap.Line srcLine = _lineMap.getLine(line);

      if (srcLine != null) {
        _jspBuilder.setLocation(srcLine.getSourceFilename(),
                                srcLine.getSourceLine(line));
      }
    }
  }

  private String badChar(int ch)
  {
    if (ch < 0)
      return "end of file";
    else if (ch == '\n' || ch == '\r')
      return "end of line";
    else if (ch >= 0x20 && ch <= 0x7f)
      return "`" + (char) ch + "'";
    else
      return "`" + (char) ch + "' (\\u" + hex(ch) + ")";
  }

  private String hex(int value)
  {
    CharBuffer cb = new CharBuffer();

    for (int b = 3; b >= 0; b--) {
      int v = (value >> (4 * b)) & 0xf;
      if (v < 10)
	cb.append((char) (v + '0'));
      else
	cb.append((char) (v - 10 + 'a'));
    }

    return cb.toString();
  }

  class Include {
    ReadStream _stream;
    int _line;
    String _uriPwd;

    Include(ReadStream stream, int line, String uriPwd)
    {
      _stream = stream;
      _line = line;
      _uriPwd = uriPwd;
    }
  }
}
