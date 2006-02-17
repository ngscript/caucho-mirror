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

package com.caucho.config;

import java.util.*;
import java.lang.reflect.*;

import javax.servlet.jsp.el.ELException;

import org.w3c.dom.Node;

import com.caucho.util.L10N;
import com.caucho.util.CharBuffer;
import com.caucho.util.NotImplementedException;

import com.caucho.el.ELParser;
import com.caucho.el.Expr;
import com.caucho.el.EL;

import com.caucho.xml.QName;

public abstract class AttributeStrategy {
  static final L10N L = new L10N(AttributeStrategy.class);

  protected AttributeStrategy()
  {
  }
 
  /**
   * Configures the parent object with the given node.
   *
   * @param builder the calling node builder (context)
   * @param bean the bean to be configured
   * @param name the name of the property
   * @param node the configuration node for the value
   */
  public void configure(NodeBuilder builder,
                        Object bean,
                        QName name,
                        Node node)
    throws Exception
  {
  }

  /**
   * Sets the named attribute of the bean.
   *
   * @param bean the bean to be set
   * @param name the attribute namee
   * @param value the attribute value
   * @throws Exception
   */
  public void setAttribute(Object bean, QName name, Object value)
    throws Exception
  {
  }

  /**
   * Evaluate as a string.
   */
  public static String evalString(String exprString)
    throws ELException
  {
    if (exprString.indexOf("${") >= 0) {
      ELParser parser = new ELParser(exprString);
      parser.setCheckEscape(true);
      Expr expr = parser.parse();

      return expr.evalString(Config.getEnvironment());
    }
    else
      return exprString;
  }

  /**
   * Evaluate as a boolean.
   */
  public static boolean evalBoolean(String exprString)
    throws ELException
  {
    if (exprString.indexOf("${") >= 0) {
      ELParser parser = new ELParser(exprString);
      parser.setCheckEscape(true);
      Expr expr = parser.parse();

      return expr.evalBoolean(Config.getEnvironment());
    }
    else
      return Expr.toBoolean(exprString, null);
  }

  /**
   * Evaluate as a long.
   */
  public static long evalLong(String exprString)
    throws ELException
  {
    if (exprString.indexOf("${") >= 0) {
      ELParser parser = new ELParser(exprString);
      parser.setCheckEscape(true);
      Expr expr = parser.parse();

      return expr.evalLong(Config.getEnvironment());
    }
    else
      return Expr.toLong(exprString, null);
  }

  /**
   * Evaluate as a double.
   */
  public static double evalDouble(String exprString)
    throws ELException
  {
    if (exprString.indexOf("${") >= 0) {
      ELParser parser = new ELParser(exprString);
      parser.setCheckEscape(true);
      Expr expr = parser.parse();

      return expr.evalDouble(Config.getEnvironment());
    }
    else
      return Expr.toDouble(exprString, null);
  }

  /**
   * Evaluate as an object.
   */
  public static Object evalObject(String exprString)
    throws ELException
  {
    if (exprString.indexOf("${") >= 0) {
      ELParser parser = new ELParser(exprString);
      parser.setCheckEscape(true);
      Expr expr = parser.parse();

      if (expr != null)
	return expr.evalObject(Config.getEnvironment());
      else
	return exprString;
    }
    else
      return exprString;
  }
}
