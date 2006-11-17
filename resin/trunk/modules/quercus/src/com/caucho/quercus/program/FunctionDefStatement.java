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

package com.caucho.quercus.program;

import java.util.ArrayList;
import java.util.HashSet;

import java.io.IOException;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.Value;

import com.caucho.quercus.expr.VarExpr;

import com.caucho.quercus.Location;

import com.caucho.vfs.WriteStream;

/**
 * Represents a function definition
 */
public class FunctionDefStatement extends Statement {
  protected Function _fun;

  public FunctionDefStatement(Location location, Function fun)
  {
    super(location);
    
    _fun = fun;
  }
  
  public Value execute(Env env)
  {
    try {
      String name = _fun.getName();

      if (env.findFunction(name) == null)
        env.addFunction(name, _fun);
    }
    catch (RuntimeException e) {
      rethrow(e, RuntimeException.class);
    }

    return null;
  }
}

