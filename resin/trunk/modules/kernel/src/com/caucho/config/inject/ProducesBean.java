/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
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

package com.caucho.config.inject;

import com.caucho.config.*;
import com.caucho.config.inject.ComponentImpl;
import com.caucho.config.j2ee.*;
import com.caucho.config.types.*;
import com.caucho.util.*;
import com.caucho.config.*;
import com.caucho.config.cfg.*;

import java.lang.reflect.*;
import java.lang.annotation.*;
import java.util.ArrayList;

import javax.annotation.*;
import javax.context.CreationalContext;
import javax.inject.manager.Bean;
import javax.inject.manager.InjectionPoint;
import javax.inject.manager.Manager;

/**
 * Configuration for a @Produces method
 */
public class ProducesBean extends ComponentImpl {
  private static final L10N L = new L10N(ProducesBean.class);

  private static final Object []NULL_ARGS = new Object[0];
  
  private final Bean _producer;
  private final Method _method;
  private final Annotation []_annotationList;

  // XXX: needs to be InjectionPoint
  private Bean []_args;

  private boolean _isBound;

  protected ProducesBean(InjectManager inject,
			 Bean producer,
			 Method method,
			 Annotation []annList)
  {
    super(inject);

    _producer = producer;
    _method = method;
    _annotationList = annList;

    setTargetType(method.getGenericReturnType());
  }

  public static ProducesBean create(InjectManager inject,
				    Bean producer,
				    Method method,
				    Annotation []annList)
  {
    return new ProducesBean(inject, producer, method, annList);
  }

  protected Bean getProducer()
  {
    return _producer;
  }

  protected Method getMethod()
  {
    return _method;
  }

  protected Annotation []getAnnotationList()
  {
    return _annotationList;
  }

  @Override
  protected void initDefault()
  {
    if (getDeploymentType() == null
	&& _producer.getDeploymentType() != null) {
      setDeploymentType(_producer.getDeploymentType());
    }
    
    super.initDefault();
  }

  public void introspect()
  {
    introspectTypes(getTargetType());
    
    Annotation []annotations = _annotationList;

    introspectAnnotations(annotations);
  }

  @Override
  protected String getDefaultName()
  { 
    String methodName = _method.getName();
      
    if (methodName.startsWith("get") && methodName.length() > 3) {
      return (Character.toLowerCase(methodName.charAt(3))
	      + methodName.substring(4));
    }
    else
      return methodName;
  }

  public boolean isInjectionPoint()
  {
    for (Class paramType : _method.getParameterTypes()) {
      if (InjectionPoint.class.equals(paramType))
	return true;
    }

    return false;
  }

  @Override
  protected Object createNew(CreationalContext context,
			     InjectionPoint ij)
  {
    try {
      ConfigContext env = (ConfigContext) context;
      
      Object factory = _webBeans.getInstance(_producer);

      if (factory == null) {
	throw new IllegalStateException(L.l("{0}: unexpected null factory for {1}",
					    this, _producer));
      }

      if (_args == null)
	bind();

      Object []args;
      if (_args.length > 0) {
	args = new Object[_args.length];

	for (int i = 0; i < args.length; i++) {
	  if (_args[i] instanceof InjectionPointBean) {
	    if (ij != null)
	      args[i] = ij;
	    else
	      throw new NullPointerException();
	  }
	  else
	    args[i] = _webBeans.getInstance(_args[i]);
	}
      }
      else
	args = NULL_ARGS;
      
      Object value = _method.invoke(factory, args);

      if (env != null)
	env.put(this, value);

      return value;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void bind()
  {
    synchronized (this) {
      if (_isBound)
	return;

      _isBound = true;
      
      String loc = InjectManager.location(_method);
    
      Type []param = _method.getGenericParameterTypes();
      Annotation [][]paramAnn = _method.getParameterAnnotations();

      _args = new Bean[param.length];

      for (int i = 0; i < param.length; i++) {
	_args[i] = bindParameter(loc, param[i], paramAnn[i]);

	if (_args[i] != null) {
	}
	else if (InjectionPoint.class.equals(param[i])) {
	  _args[i] = createInjectionPointBean(getManager());
	}
	else {
	  throw error(_method, L.l("Type '{0}' for method parameter #{1} has no matching component.",
				   getSimpleName(param[i]), i));
	}
      }
    }
  }

  public Bean bindInjectionPoint(InjectionPoint ij)
  {
    return new ProducesInjectionPointBean(this, ij);
  }

  protected InjectionPointBean createInjectionPointBean(Manager manager)
  {
    return new InjectionPointBean(manager);
  }

  public String toString()
  {
    StringBuilder sb = new StringBuilder();

    sb.append(getClass().getSimpleName());
    sb.append("[");

    sb.append(getTargetSimpleName());
    sb.append(", ");
    sb.append(_method.getDeclaringClass().getSimpleName());
    sb.append(".");
    sb.append(_method.getName());
    sb.append("()");
    
    sb.append(", {");

    boolean isFirst = true;
    for (Object obj : getBindings()) {
      Annotation ann = (Annotation) obj;
      
      if (! isFirst)
	sb.append(", ");

      sb.append(ann);

      isFirst = false;
    }

    sb.append("}");
    
    if (getName() != null) {
      sb.append(", name=");
      sb.append(getName());
    }

    if (getDeploymentType() != null) {
      sb.append(", @");
      sb.append(getDeploymentType().getSimpleName());
    }
    sb.append("]");

    return sb.toString();
  }
}
