/*
 * Copyright (c) 1998-2003 Caucho Technology -- all rights reserved
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

package javax.el;

import java.beans.FeatureDescriptor;
import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.*;

/**
 * Resolves properties based on arrays.
 */
public class ArrayELResolver extends ELResolver {
  private final static Logger log
    = Logger.getLogger(ArrayELResolver.class.getName());
  
  private final boolean _isReadOnly;
  
  public ArrayELResolver()
  {
    _isReadOnly = false;
  }
  
  public ArrayELResolver(boolean isReadOnly)
  {
    _isReadOnly = isReadOnly;
  }

  @Override
  public Class<?> getCommonPropertyType(ELContext context, Object base)
  {
    if (base == null)
      return null;
    else if (base.getClass().isArray())
      return base.getClass().getComponentType();
    else
      return null;
  }

  @Override
  public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context,
							   Object base)
  {
    if (base == null)
      return null;
    else if (base.getClass().isArray()) {
      context.setPropertyResolved(true);

      return null;
    }
    else
      return null;
  }

  @Override
  public Class<?> getType(ELContext context,
			  Object base,
			  Object property)
  {
    if (base == null)
      return null;
    else if (base.getClass().isArray()) {
      context.setPropertyResolved(true);

      return base.getClass().getComponentType();
    }
    else
      return null;
  }

  @Override
  public Object getValue(ELContext context,
			 Object base,
			 Object property)
  {
    if (base == null)
      return null;
    else if (base.getClass().isArray()) {
      context.setPropertyResolved(true);

      int index = 0;

      if (property instanceof Number)
	index = ((Number) property).intValue();
      else if (property instanceof String) {
	try {
	  index = Integer.parseInt((String) property);
	} catch (Exception e) {
	  throw new ELException("can't convert '" + property + "' to long.");
	}
      }
      else
	throw new ELException("can't convert '" + property + "' to long.");

      if (0 <= index && index < Array.getLength(base))
	return Array.get(base, index);
      else
	return null;
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isReadOnly(ELContext context,
			    Object base,
			    Object property)
  {
    if (base == null)
      return false;
    else if (base.getClass().isArray()) {
      context.setPropertyResolved(true);

      return _isReadOnly;
    }
    else
      return false;
  }

  @Override
  public void setValue(ELContext context,
		       Object base,
		       Object property,
		       Object value)
  {
    if (base == null) {
    }
    else if (base.getClass().isArray()) {
      context.setPropertyResolved(true);

      int index = 0;

      if (property instanceof Number)
	index = ((Number) property).intValue();
      else if (property instanceof String) {
	try {
	  index = Integer.parseInt((String) property);
	} catch (Exception e) {
	  log.log(Level.FINE, e.toString(), e);
	}
      }

      Array.set(base, index, value);
    }
  }
}
