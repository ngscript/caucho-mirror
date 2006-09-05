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

package com.caucho.el;

import java.util.logging.*;

import javax.el.*;

import com.caucho.util.*;

/**
 * Implementation of the method expression.
 */
public class MethodExpressionImpl extends MethodExpression
{
  protected static final Logger log
    = Logger.getLogger(MethodExpressionImpl.class.getName());
  protected static final L10N L = new L10N(MethodExpressionImpl.class);

  private final String _expressionString;
  private final Expr _expr;
  private final Class _expectedType;
  private final Class []_expectedArgs;

  public MethodExpressionImpl(Expr expr,
			      String expressionString,
			      Class<?> expectedType,
			      Class<?> []expectedArgs)
  {
    _expr = expr;
    _expressionString = expressionString;
    _expectedType = expectedType;
    _expectedArgs = expectedArgs;
  }

  public boolean isLiteralText()
  {
    return _expr.isLiteralText();
  }

  public String getExpressionString()
  {
    return _expressionString;
  }
  
  public MethodInfo getMethodInfo(ELContext context)
    throws PropertyNotFoundException,
	   MethodNotFoundException,
	   ELException
  {
    return _expr.getMethodInfo(context, _expectedType, _expectedArgs);
  }

  public Object invoke(ELContext context,
		       Object []params)
    throws PropertyNotFoundException,
	   MethodNotFoundException,
	   ELException
  {
    if (params == null && _expectedArgs.length != 0
	|| params != null && params.length != _expectedArgs.length) {
      throw new IllegalArgumentException(L.l("expected arguments do not match actual arguments for '{0}'", _expr.toString()));
    }
      
    return _expr.invoke(context, _expectedArgs, params);
  }

  public int hashCode()
  {
    return _expr.hashCode();
  }
  
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (! (o instanceof MethodExpressionImpl))
      return false;

    MethodExpressionImpl expr = (MethodExpressionImpl) o;

    return _expr.equals(expr._expr);
  }

  public String toString()
  {
    return "MethodExpressionImpl[" + getExpressionString() + "]";
  }
}
