/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.jstl.el;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;
import javax.servlet.jsp.el.ELException;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.*;

import org.w3c.dom.*;

import com.caucho.log.Log;
import com.caucho.util.*;
import com.caucho.vfs.*;
import com.caucho.jsp.PageContextImpl;
import com.caucho.el.*;
import com.caucho.xsl.*;
import com.caucho.jstl.NameValueTag;

public class XmlTransformTag extends BodyTagSupport implements NameValueTag {
  private static final Logger log = Log.open(XmlTransformTag.class);
  private static final L10N L = new L10N(XmlTransformTag.class);
  
  private Expr _xml;
  private Expr _xslt;

  private Expr _xmlSystemId;
  private Expr _xsltSystemId;
  
  private String _var;
  private String _scope;

  private Expr _result;

  private ArrayList<String> _paramNames = new ArrayList<String>();
  private ArrayList<String> _paramValues = new ArrayList<String>();
  
  /**
   * Sets the JSP-EL XML value.
   */
  public void setXml(Expr xml)
  {
    setDoc(xml);
  }
  
  /**
   * Sets the JSP-EL XML value.
   */
  public void setDoc(Expr xml)
  {
    _xml = xml;
  }

  /**
   * Sets the JSP-EL XML value.
   */
  public void setXslt(Expr xslt)
  {
    _xslt = xslt;
  }

  /**
   * Sets the JSP-EL XML system id expr.
   */
  public void setXmlSystemId(Expr xmlSystemId)
  {
    _xmlSystemId = xmlSystemId;
  }

  /**
   * Sets the JSP-EL XML system id expr.
   */
  public void setDocSystemId(Expr xmlSystemId)
  {
    _xmlSystemId = xmlSystemId;
  }

  /**
   * Sets the JSP-EL XSLT system id.
   */
  public void setXsltSystemId(Expr xsltSystemId)
  {
    _xsltSystemId = xsltSystemId;
  }

  /**
   * Sets the variable
   */
  public void setVar(String var)
  {
    _var = var;
  }

  /**
   * Sets the scope
   */
  public void setScope(String scope)
  {
    _scope = scope;
  }

  /**
   * Sets the result
   */
  public void setResult(Expr result)
  {
    _result = result;
  }
  
  /**
   * Adds a parameter.
   */
  public void addParam(String name, String value)
  {
    _paramNames.add(name);
    _paramValues.add(value);
  }

  /**
   * Process the tag.
   */
  public int doStartTag()
    throws JspException
  {
    _paramNames.clear();
    _paramValues.clear();

    return EVAL_BODY_BUFFERED;
  }

  /**
   * Process the tag.
   */
  public int doEndTag()
    throws JspException
  {
    try {
      PageContextImpl pageContext = (PageContextImpl) this.pageContext;
      
      JspWriter out = pageContext.getOut();

      TransformerFactory factory = TransformerFactory.newInstance();

      Source source = getSource(_xslt, _xsltSystemId);

      Transformer transformer = factory.newTransformer(source);

      for (int i = 0; i < _paramNames.size(); i++) {
        String name = _paramNames.get(i);
        String value = _paramValues.get(i);

        transformer.setParameter(name, value);
      }

      if (_xml != null)
	source = getSource(_xml, _xmlSystemId);
      else {
	BodyContent bodyContent = getBodyContent();

	source = new StreamSource(bodyContent.getReader());
	source.setSystemId(((HttpServletRequest) pageContext.getRequest()).getRequestURI());
      }

      Result result;
      Node top = null;

      if (_result != null) {
        result = (Result) _result.evalObject(pageContext);
      }
      else if (_var != null) {
        top = new com.caucho.xml.QDocument();
        
        result = new DOMResult(top);
      }
      else
        result = new StreamResult(out);

      transformer.transform(source, result);

      if (_var != null)
        CoreSetTag.setValue(pageContext, _var, _scope, top);
    } catch (Exception e) {
      throw new JspException(e);
    }

    return SKIP_BODY;
  }

  private Source getSource(Expr xmlExpr, Expr systemIdExpr)
    throws JspException
  {
    try {
      PageContextImpl pageContext = (PageContextImpl) this.pageContext;
      
      Object xmlObj = xmlExpr.evalObject(pageContext);
      String systemId = null;
      Source source = null;

      if (systemIdExpr != null)
	systemId = systemIdExpr.evalString(pageContext);
      
      source = convertToSource(xmlObj, systemId);

      return source;
    } catch (ELException e) {
      throw new JspException(e);
    }
  }

  public static Source convertToSource(Object xmlObj, String systemId)
    throws JspException
  {
    if (xmlObj instanceof String) {
      ReadStream is = Vfs.openString((String) xmlObj);

      return new StreamSource(is, systemId);
    }
    else if (xmlObj instanceof InputStream) {
      return new StreamSource((InputStream) xmlObj, systemId);
    }
    else if (xmlObj instanceof Reader) {
      return new StreamSource((Reader) xmlObj, systemId);
    }
    else if (xmlObj instanceof Node) {
      return new DOMSource((Node) xmlObj, systemId);
    }
    else if (xmlObj instanceof NodeList) {
      return new DOMSource(((NodeList) xmlObj).item(0), systemId);
    }
    else if (xmlObj instanceof Source)
      return  (Source) xmlObj;
    else if (xmlObj instanceof ArrayList) {
      ArrayList list = (ArrayList) xmlObj;

      if (list.size() > 0)
	return convertToSource(list.get(0), systemId);
    }
    
    throw new JspException(L.l("unknown xml object type '{0}' '{1}'",
			       xmlObj, xmlObj.getClass()));
  }
}
