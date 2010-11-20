/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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

package com.caucho.config.reflect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.enterprise.inject.spi.AnnotatedCallable;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;

import com.caucho.inject.Module;


/**
 * Abstract introspected view of a Bean
 */
@Module
public class AnnotatedParameterImpl<T>
  extends AnnotatedElementImpl implements AnnotatedParameter<T>
{
  private AnnotatedCallable<T> _callable;
  private int _position;
  
  public AnnotatedParameterImpl(AnnotatedCallable<T> callable,
                                Type type,
                                Annotation []annList,
                                int position)
  {
    super(createBaseType(callable, type), null, annList);

    _callable = callable;
    _position = position;
  }
  
  protected static BaseType createBaseType(AnnotatedCallable<?> callable,
                                           Type type)
  {
    if (callable != null && callable.getDeclaringType() != null) {
      AnnotatedType<?> declAnnType = callable.getDeclaringType();
      
      Type declType = declAnnType.getBaseType();

      if (declType instanceof Class<?>)
        return createBaseType(type);
      
      if (! (declAnnType instanceof AnnotatedTypeImpl<?>))
        return createBaseType(type);
      
      AnnotatedTypeImpl<?> declAnnTypeImpl = (AnnotatedTypeImpl<?>) declAnnType;
      
      BaseType declBaseType = declAnnTypeImpl.getBaseTypeImpl();
      
      BaseType paramType = declBaseType.createForTarget(type, declBaseType.getParamMap());
      
      return paramType;
    }
    
    return createBaseType(type);
  }

  @Override
  public AnnotatedCallable<T> getDeclaringCallable()
  {
    return _callable;
  }

  @Override
  public int getPosition()
  {
    return _position;
  }
}