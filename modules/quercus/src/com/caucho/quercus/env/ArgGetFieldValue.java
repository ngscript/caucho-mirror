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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.quercus.env;

import com.caucho.vfs.WriteStream;

import java.util.IdentityHashMap;

/**
 * Represents an field-get argument which might be a call to a reference.
 */
public class ArgGetFieldValue extends Value {
  private final Value _obj;
  private final String _index;

  public ArgGetFieldValue(Value obj, String index)
  {
    _obj = obj;
    _index = index;
  }

  /**
   * Converts to a reference variable.
   */
  public Var toRefVar()
  {
    return _obj.getFieldRef(_index).toRefVar();
  }

  /**
   * Converts to a reference variable.
   */
  public Value toRefValue()
  {
    return _obj.getFieldRef(_index);
  }

  /**
   * Converts to a value.
   */
  public Value toValue()
  {
    return _obj.getField(_index);
  }

  /**
   * Converts to a variable.
   */
  public Var toVar()
  {
    return new Var();
  }
}

