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

package com.caucho.el;

import java.io.*;
import java.util.logging.*;

import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.jsp.el.ELException;

import com.caucho.vfs.*;

/**
 * Represents a numeric comparison operation: lt, gt, le, ge.
 */
public class CmpExpr extends AbstractBooleanExpr {
  private int _op;
  private Expr _left;
  private Expr _right;

  /**
   * Creates a comparison expression
   *
   * @param op the lexical code for the operation
   * @param left the left subexpression
   * @param right the right subexpression
   */
  public CmpExpr(int op, Expr left, Expr right)
  {
    _op = op;
    _left = left;
    _right = right;
  }

  /**
   * Returns true if this is a constant expression.
   */
  public boolean isConstant()
  {
    return _left.isConstant() && _right.isConstant();
  }
  
  /**
   * Evaluate the expression as a boolean.
   *
   * @param env the variable environment
   */
  public boolean evalBoolean(VariableResolver env)
    throws ELException
  {
    Object aObj = _left.evalObject(env);
    Object bObj = _right.evalObject(env);

    if (aObj == bObj)
      return _op == LE || _op == GE;

    if (aObj instanceof Double || aObj instanceof Float ||
        bObj instanceof Double || bObj instanceof Float) {
      double a = toDouble(aObj, env);
      double b = toDouble(bObj, env);

      switch (_op) {
      case LT: return a < b;
      case LE: return a <= b;
      case GT: return a > b;
      case GE: return a >= b;
      }
    }
    
    if (aObj instanceof Number || bObj instanceof Number) {
      long a = toLong(aObj, env);
      long b = toLong(bObj, env);

      switch (_op) {
      case LT: return a < b;
      case LE: return a <= b;
      case GT: return a > b;
      case GE: return a >= b;
      }
    }

    if (aObj instanceof String || bObj instanceof String) {
      String a = toString(aObj, env);
      String b = toString(bObj, env);

      int cmp = a.compareTo(b);

      switch (_op) {
      case LT: return cmp < 0;
      case LE: return cmp <= 0;
      case GT: return cmp > 0;
      case GE: return cmp >= 0;
      }
    }

    if (aObj instanceof Comparable) {
      int cmp = ((Comparable) aObj).compareTo(bObj);

      switch (_op) {
      case LT: return cmp < 0;
      case LE: return cmp <= 0;
      case GT: return cmp > 0;
      case GE: return cmp >= 0;
      }
    }

    if (bObj instanceof Comparable) {
      int cmp = ((Comparable) bObj).compareTo(aObj);

      switch (_op) {
      case LT: return cmp > 0;
      case LE: return cmp >= 0;
      case GT: return cmp < 0;
      case GE: return cmp <= 0;
      }
    }

    ELException e = new ELException(L.l("can't compare {0} and {1}.",
                                        aObj, bObj));

    error(e, env);

    return false;
  }

  /**
   * Prints the code to create an LongLiteral.
   */
  public void printCreate(WriteStream os)
    throws IOException
  {
    os.print("new com.caucho.el.CmpExpr(");
    os.print(_op + ", ");
    _left.printCreate(os);
    os.print(", ");
    _right.printCreate(os);
    os.print(")");
  }

  /**
   * Returns true for equal strings.
   */
  public boolean equals(Object o)
  {
    if (! (o instanceof CmpExpr))
      return false;

    CmpExpr expr = (CmpExpr) o;

    return (_op == expr._op &&
            _left.equals(expr._left) &&
            _right.equals(expr._right));
  }
  
  /**
   * Returns a readable representation of the expr.
   */
  public String toString()
  {
    String op;

    switch (_op) {
    case LT:
      op = " lt";
      break;
    case LE:
      op = " le ";
      break;
    case GT:
      op = " gt ";
      break;
    case GE:
      op = " ge ";
      break;
    default:
      op = " unknown(" + _op + ") ";
      break;
    }
        
    return "(" + _left + op + _right + ")";
  }
}
