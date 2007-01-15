/*
 * Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
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
 * @author Sam
 */

package com.caucho.server.rewrite;

import com.caucho.config.ConfigException;
import com.caucho.config.types.RawString;
import com.caucho.el.ELParser;
import com.caucho.el.Expr;
import com.caucho.util.L10N;

import javax.annotation.PostConstruct;
import javax.el.ELContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * A {@link RewriteDispatch} condition that is an el expression.
 */
public class ExprCondition
  extends AbstractCondition
{
  private static final L10N L = new L10N(ExprCondition.class);

  private final AbstractConditions _conditions;

  private Expr _expr;

  public ExprCondition(AbstractConditions conditions)
  {
    _conditions = conditions;
  }

  public String getTagName()
  {
    return "expr";
  }

  /**
   * Sets the el expression.
   */
  public void setText(RawString expr)
  {
    ELContext elContext = _conditions.getParseContext();
    _expr = new ELParser(elContext, expr.getValue()).parse();
  }

  @PostConstruct
  public void init()
  {
    if (_expr == null)
      throw new ConfigException(L.l("`{0}' is required", "#text"));
  }

  public boolean evaluate(RewriteContext rewriteContext)
  {
    return _expr.evalBoolean(rewriteContext);
  }
}
