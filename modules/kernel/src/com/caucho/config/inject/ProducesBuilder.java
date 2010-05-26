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

package com.caucho.config.inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.Specializes;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Named;
import javax.inject.Qualifier;

import com.caucho.config.ConfigException;
import com.caucho.config.Names;
import com.caucho.config.program.Arg;
import com.caucho.config.program.BeanArg;
import com.caucho.inject.Module;
import com.caucho.util.L10N;

/**
 * Builder for produces beans.
 */
@Module
public class ProducesBuilder {
  private static final L10N L = new L10N(ProducesBuilder.class);
  
  private InjectManager _manager;

  public ProducesBuilder(InjectManager manager)
  {
    _manager = manager;
  }

  /**
   * Introspects the methods for any @Produces
   */
  public <X> void introspectProduces(Bean<X> bean, AnnotatedType<X> beanType)
  {
    for (AnnotatedMethod<? super X> beanMethod : beanType.getMethods()) {
      if (beanMethod.isAnnotationPresent(Produces.class)) {
        AnnotatedMethod<? super X> disposesMethod 
          = findDisposesMethod(beanType, beanMethod);
        
        addProduces(bean, beanType, beanMethod, disposesMethod);
      }
    }
    
    for (AnnotatedField<?> beanField : beanType.getFields()) {
      if (beanField.isAnnotationPresent(Produces.class))
        addProduces(bean, beanType, beanField);
    }
  }

  protected <X,T> void addProduces(Bean<X> bean,
                                   AnnotatedType<X> beanType,
                                   AnnotatedMethod<? super X> producesMethod,
                                   AnnotatedMethod<? super X> disposesMethod)
  {
    if (producesMethod.getJavaMember().getDeclaringClass() != beanType.getJavaClass()
        && ! beanType.isAnnotationPresent(Specializes.class))
      return;
    
    Arg []producesArgs = introspectArguments(bean, producesMethod);
    Arg []disposesArgs = null;
    
    if (disposesMethod != null)
      disposesArgs = introspectDisposesArgs(disposesMethod.getParameters());

    ProducesBean<X,T> producesBean
      = ProducesBean.create(_manager, bean, 
                            producesMethod, producesArgs,
                            disposesMethod, disposesArgs);

    // bean.init();

    // _manager.addBean(producesBean);
    _manager.addProducesBean(producesBean);
  }
  
  private <X> AnnotatedMethod<? super X>
  findDisposesMethod(AnnotatedType<X> beanType,
                     AnnotatedMethod<? super X> producesMethod)
  {
    for (AnnotatedMethod beanMethod : beanType.getMethods()) {
      List<AnnotatedParameter<?>> params = beanMethod.getParameters();
      
      if (params.size() == 0)
        continue;
      
      AnnotatedParameter<?> param = params.get(0);
      
      if (! param.isAnnotationPresent(Disposes.class))
        continue;
      
      if (! producesMethod.getBaseType().equals(param.getBaseType()))
        continue;


      // XXX: check @Qualifiers
      
      return beanMethod;
    }
    
    return null;
  }

  protected <X> void addProduces(Bean<X> bean,
                                 AnnotatedType<X> beanType,
                                 AnnotatedField<?> beanField)
  {
    Class<?> beanClass = beanType.getJavaClass();
    
    if (beanField.getJavaMember().getDeclaringClass() != beanClass
        && ! beanClass.isAnnotationPresent(Specializes.class))
      return;
    
    ProducesFieldBean producesBean
      = ProducesFieldBean.create(_manager, bean, beanField);

    // bean.init();

    _manager.addProducesFieldBean(producesBean);
  }

  protected <X,T> Arg<T> []introspectArguments(Bean<X> bean,
                                               AnnotatedMethod<T> method)
  {
    List<AnnotatedParameter<T>> params = method.getParameters();
    Method javaMethod = method.getJavaMember();
    
    Arg<T> []args = new Arg[params.size()];

    for (int i = 0; i < args.length; i++) {
      AnnotatedParameter<?> param = params.get(i);
      
      if (param.isAnnotationPresent(Disposes.class))
        throw new ConfigException(L.l("'{0}.{1}' is an invalid producer method because a parameter is annotated with @Disposes",
                                      javaMethod.getDeclaringClass().getName(), 
                                      javaMethod.getName()));
      
      InjectionPoint ip = new InjectionPointImpl(_manager,
                                                 bean,
                                                 param);

      if (InjectionPoint.class.equals(param.getBaseType()))
        args[i] = new InjectionPointArg();
      else
        args[i] = new BeanArg(_manager,
                              param.getBaseType(), 
                              getQualifiers(param),
                              ip);
    }

    return args;
  }

  protected <X> Arg<X> []introspectDisposesArgs(List<AnnotatedParameter<X>> params)
  {
    Arg<X> []args = new Arg[params.size()];

    for (int i = 0; i < args.length; i++) {
      AnnotatedParameter<X> param = params.get(i);
      
      InjectionPoint ip = null;

      if (param.isAnnotationPresent(Disposes.class))
        args[i] = null;
      else
        args[i] = new BeanArg(_manager,
                              param.getBaseType(), 
                              getQualifiers(param),
                              ip);
    }

    return args;
  }
  
  private Annotation []getQualifiers(Annotated annotated)
  {
    ArrayList<Annotation> qualifierList = new ArrayList<Annotation>();

    for (Annotation ann : annotated.getAnnotations()) {
      if (ann.annotationType().equals(Named.class)) {
        Named named = (Named) ann;
        
        String namedValue = named.value();

        if ("".equals(namedValue)) {
          String name = ((Class) annotated.getBaseType()).getSimpleName();

          ann = Names.create(name);
        }

        qualifierList.add(ann);

      }
      else if (ann.annotationType().isAnnotationPresent(Qualifier.class)) {
        qualifierList.add(ann);
      }
    }

    if (qualifierList.size() == 0)
      qualifierList.add(CurrentLiteral.CURRENT);

    Annotation []qualifiers = new Annotation[qualifierList.size()];
    qualifierList.toArray(qualifiers);

    return qualifiers;
  }
}
