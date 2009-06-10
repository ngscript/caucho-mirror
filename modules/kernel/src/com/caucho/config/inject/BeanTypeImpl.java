/*
 * Copyright (c) 1998-2009 Caucho Technology -- all rights reserved
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

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.*;
import javax.enterprise.context.ScopeType;
import javax.enterprise.inject.deployment.DeploymentType;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedMethod;

/**
 * Abstract introspected view of a Bean
 */
public class BeanTypeImpl extends AnnotatedElementImpl implements AnnotatedType
{
  private static final Logger log
    = Logger.getLogger(BeanTypeImpl.class.getName());

  private Class _javaClass;

  private Set<AnnotatedConstructor> _constructorSet
    = new LinkedHashSet<AnnotatedConstructor>();

  private Set<AnnotatedField> _fieldSet
    = new LinkedHashSet<AnnotatedField>();

  private Set<AnnotatedMethod> _methodSet
    = new LinkedHashSet<AnnotatedMethod>();
  
  public BeanTypeImpl(Type type, Class javaClass)
  {
    super(type, null, javaClass.getDeclaredAnnotations());

    _javaClass = javaClass;

    introspect(javaClass);
  }
  
  /**
   * Returns the concrete Java class
   */
  public Class<?> getJavaClass()
  {
    return _javaClass;
  }

  /**
   * Returns the abstract introspected constructors
   */
  public Set<AnnotatedConstructor> getConstructors()
  {
    return _constructorSet;
  }

  /**
   * Returns the abstract introspected methods
   */
  public Set<AnnotatedMethod> getMethods()
  {
    return _methodSet;
  }

  /**
   * Returns the matching method, creating one if necessary.
   */
  public AnnotatedMethod createMethod(Method method)
  {
    for (AnnotatedMethod annMethod : _methodSet) {
      if (BeanMethodImpl.isMatch(annMethod.getJavaMember(), method)) {
	return annMethod;
      }
    }

    AnnotatedMethod annMethod = new BeanMethodImpl(this, null, method);

    _methodSet.add(annMethod);

    return annMethod;
  }

  /**
   * Returns the abstract introspected fields
   */
  public Set<AnnotatedField> getFields()
  {
    return _fieldSet;
  }

  private void introspect(Class cl)
  {
    introspectInheritedAnnotations(cl.getSuperclass());

    introspectFields(cl);

    for (Method method : cl.getDeclaredMethods()) {
      if (hasBeanAnnotation(method)) {
	_methodSet.add(new BeanMethodImpl(this, null, method));
      }
    }

    if (! cl.isInterface()) {
      for (Constructor ctor : cl.getDeclaredConstructors()) {
	_constructorSet.add(new BeanConstructorImpl(this, ctor));
      }

      if (_constructorSet.size() == 0) {
	try {
	  Constructor ctor = cl.getConstructor(new Class[0]);
	  _constructorSet.add(new BeanConstructorImpl(this, ctor));
	} catch (NoSuchMethodException e) {
	  log.log(Level.FINE, e.toString(), e);
	}
      }
    }
  }

  private void introspectFields(Class cl)
  {
    if (cl == null)
      return;

    introspectFields(cl.getSuperclass());
    
    for (Field field : cl.getDeclaredFields()) {
      if (hasBeanAnnotation(field.getAnnotations())) {
	_fieldSet.add(new BeanFieldImpl(this, field));
      }
    }
  }
  
  private void introspectInheritedAnnotations(Class cl)
  {
    if (cl == null)
      return;
    
    for (Annotation ann : cl.getDeclaredAnnotations()) {
      if (! ann.annotationType().isAnnotationPresent(Inherited.class)) {
	continue;
      }

      if (isAnnotationPresent(cl)) {
	continue;
      }
      
      if (ann.annotationType().isAnnotationPresent(ScopeType.class)
	  && hasMetaAnnotation(getAnnotations(), ScopeType.class)) {
	continue;
      }
      
      if (ann.annotationType().isAnnotationPresent(DeploymentType.class)
	  && hasMetaAnnotation(getAnnotations(), DeploymentType.class)) {
	continue;
      }

      addAnnotation(ann);
    }

    introspectInheritedAnnotations(cl.getSuperclass());
  }

  private boolean hasBeanAnnotation(Method method)
  {
    if (hasBeanAnnotation(method.getAnnotations()))
      return true;

    Annotation [][]paramAnn = method.getParameterAnnotations();
    if (paramAnn != null) {
      for (int i = 0; i < paramAnn.length; i++) {
	if (hasBeanAnnotation(paramAnn[i]))
	  return true;
      }
    }

    return false;
  }

  private boolean hasBeanAnnotation(Annotation []annotations)
  {
    if (annotations == null)
      return false;
    
    for (Annotation ann : annotations) {
      if (isBeanAnnotation(ann.annotationType()))
	return true;

      for (Annotation metaAnn : ann.annotationType().getAnnotations()) {
	if (isBeanAnnotation(metaAnn.annotationType()))
	  return true;
      }
    }

    return false;
  }

  private boolean hasMetaAnnotation(Set<Annotation> annotations,
				    Class metaAnnType)
  {
    if (annotations == null)
      return false;
    
    for (Annotation ann : annotations) {
      for (Annotation metaAnn : ann.annotationType().getAnnotations()) {
	if (metaAnnType.equals(metaAnn.annotationType())) {
	  return true;
	}
      }
    }

    return false;
  }

  private boolean isBeanAnnotation(Class annType)
  {
    String name = annType.getName();
    
    return name.startsWith("javax.");
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _javaClass + "]";
  }
}
