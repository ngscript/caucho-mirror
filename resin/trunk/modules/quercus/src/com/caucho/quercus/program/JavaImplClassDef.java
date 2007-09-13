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

import com.caucho.quercus.Quercus;
import com.caucho.quercus.env.*;
import com.caucho.quercus.expr.Expr;
import com.caucho.quercus.expr.LiteralExpr;
import com.caucho.quercus.function.Marshal;
import com.caucho.quercus.function.MarshalFactory;
import com.caucho.quercus.module.ModuleContext;
import com.caucho.util.L10N;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents an introspected Java class.
 */
public class JavaImplClassDef extends ClassDef {
  private final static Logger log
    = Logger.getLogger(JavaImplClassDef.class.getName());
  private final static L10N L = new L10N(JavaImplClassDef.class);

  private final ModuleContext _moduleContext;

  private final String _name;
  private final Class _type;

  private final HashMap<String, Expr> _constMap
    = new HashMap<String, Expr>();

  // JavaMethod
  private final FunctionMap _functionMap
    = new FunctionMap();

  private JavaMethod _cons;

  public JavaImplClassDef(ModuleContext moduleContext,
			   String name,
			   Class type)
  {
    super(name, null, new String[0]);

    _moduleContext = moduleContext;

    _name = name;

    _type = type;
  }

  /**
   * Returns the class name.
   */
  public String getName()
  {
    return _name;
  }

  public Class getType()
  {
    return _type;
  }

  /**
   * Creates a new instance.
   */
  public ObjectValue newInstance(Env env, QuercusClass qClass)
  {
    return new ObjectExtValue(qClass);
  }

  /**
   * Eval a method
   */
  public Value callMethod(Env env, Value obj, String name, Expr []args)
  {
    AbstractJavaMethod method = _functionMap.getFunction(name);

    if (method == null) {
      env.warning(env.getLocation(), L.l("{0}::{1} is an unknown method.",
                                         _name, name));

      return NullValue.NULL;
    }

    return method.call(env, obj, args);
  }

  /**
   * Eval a method
   */
  public Value callMethod(Env env, Value obj, String name, Value []args)
  {
    return getMethod(env, name).call(env, obj, args);
  }

  /**
   * Eval a method
   */
  public Value callMethod(Env env, Object obj, String name)
  {
    return getMethod(env, name).call(env, obj);
  }

  /**
   * Eval a method
   */
  public Value callMethod(Env env, Object obj, String name, Value a1)
  {
    return getMethod(env, name).call(env, obj, a1);
  }

  /**
   * Eval a method
   */
  public Value callMethod(Env env, Object obj, String name,
                          Value a1, Value a2)
  {
    return getMethod(env, name).call(env, obj, a1, a2);
  }

  /**
   * Eval a method
   */
  public Value callMethod(Env env, Object obj, String name,
                          Value a1, Value a2, Value a3)
  {
    return getMethod(env, name).call(env, obj, a1, a2, a3);
  }

  /**
   * Eval a method
   */
  public Value callMethod(Env env, Object obj, String name,
                          Value a1, Value a2, Value a3, Value a4)
  {
    return getMethod(env, name).call(env, obj, a1, a2, a3, a4);
  }

  /**
   * Eval a method
   */
  public Value callMethod(Env env, Object obj, String name,
                          Value a1, Value a2, Value a3, Value a4, Value a5)
  {
    return getMethod(env, name).call(env, obj, a1, a2, a3, a4, a5);
  }

  private AbstractJavaMethod getMethod(Env env, String name)
  {
    AbstractJavaMethod method = _functionMap.getFunction(name);

    if (method == null) {
      env.error(env.getLocation(), "'" + name + "' is an unknown method.");
    }

    return method;
  }

  /**
   * Initialize the quercus class.
   */
  public void initClass(QuercusClass cl)
  {
    if (_cons != null) {
      cl.setConstructor(_cons);
      cl.addMethod("__construct", _cons);
    }

    for (Map.Entry<String,AbstractJavaMethod> entry : _functionMap.entrySet()) {
      cl.addMethod(entry.getKey(), entry.getValue());
    }

    for (Map.Entry<String,Expr> entry : _constMap.entrySet()) {
      cl.addConstant(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Creates a new instance.
   */
  public void initInstance(Env env, Value value)
  {
  }

  /**
   * Eval new
   */
  @Override
  public Value callNew(Env env, Expr []args)
  {
    return null;
  }

  /**
   * Eval new
   */
  @Override
  public Value callNew(Env env, Value []args)
  {
    return null;
  }

  /**
   * Returns the constructor
   */
  public AbstractFunction findConstructor()
  {
    return null;
  }

  /**
   * Introspects the Java class.
   */
  public void introspect(ModuleContext moduleContext)
  {
    introspectConstants(_type);
    introspectMethods(moduleContext, _type);
    introspectFields(moduleContext, _type);
  }

  private Method getConsMethod(Class type)
  {
    Method []methods = type.getMethods();

    for (int i = 0; i < methods.length; i++) {
      Method method = methods[i];
      
      if (! method.getName().equals("__construct"))
	continue;
      if (! Modifier.isStatic(method.getModifiers()))
	continue;
      if (! Modifier.isPublic(method.getModifiers()))
	continue;

      return method;
    }

    return null;
  }

  /**
   * Introspects the Java class.
   */
  private void introspectFields(ModuleContext moduleContext, Class type)
  {
    MarshalFactory marshalFactory = moduleContext.getMarshalFactory();
    
    // Introspect public non-static fields
    Field[] fields = type.getFields();

    for (Field field : fields) {
      if (Modifier.isStatic(field.getModifiers()))
        continue;

      Marshal marshal = marshalFactory.create(field.getType(), false);
      
      //_fieldMap.put(field.getName(), new FieldMarshalPair(field, marshal));
    }


   // introspectFields(quercus, type.getSuperclass());
  }

  /**
   * Introspects the Java class.
   */
  private void introspectConstants(Class type)
  {
    if (type == null || type.equals(Object.class))
      return;

    if (! Modifier.isPublic(type.getModifiers()))
      return;

    Class []ifcs = type.getInterfaces();

    for (Class ifc : ifcs) {
      introspectConstants(ifc);
    }

    Field []fields = type.getDeclaredFields();

    for (Field field : fields) {
      if (_constMap.get(field.getName()) != null)
        continue;
      else if (! Modifier.isPublic(field.getModifiers()))
        continue;
      else if (! Modifier.isStatic(field.getModifiers()))
        continue;
      else if (! Modifier.isFinal(field.getModifiers()))
        continue;

      try {
        Value value = Quercus.objectToValue(field.get(null));

        if (value != null)
          _constMap.put(field.getName().intern(), new LiteralExpr(value));
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }

    introspectConstants(type.getSuperclass());
  }

  /**
   * Introspects the Java class.
   */
  private void introspectMethods(ModuleContext moduleContext, Class type)
  {
    if (type == null || type.equals(Object.class))
      return;

    Class []ifcs = type.getInterfaces();

    for (Class ifc : ifcs) {
      introspectMethods(moduleContext, ifc);
    }

    Method []methods = type.getDeclaredMethods();

    for (Method method : methods) {
      if (! Modifier.isPublic(method.getModifiers()))
        continue;
      else if (! Modifier.isStatic(method.getModifiers()))
        continue;

      JavaMethod javaMethod = new JavaMethod(moduleContext, method);

      if (method.getName().equals("__construct"))
	_cons = javaMethod;

      _functionMap.addFunction(method.getName(), javaMethod);
    }

    introspectMethods(moduleContext, type.getSuperclass());
  }

  private class FieldMarshalPair {
    public final Field _field;
    public final Marshal _marshal;

    public FieldMarshalPair(Field field,
			    Marshal marshal)
    {
      _field = field;
      _marshal = marshal;
    }
  }
}

