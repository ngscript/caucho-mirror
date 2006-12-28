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
 * @author Rodrigo Westrupp
 */

package com.caucho.amber.type;

import com.caucho.amber.field.AmberField;
import com.caucho.amber.field.AmberFieldCompare;
import com.caucho.amber.manager.AmberPersistenceUnit;
import com.caucho.amber.table.Column;
import com.caucho.amber.table.Table;
import com.caucho.bytecode.JClass;
import com.caucho.bytecode.JClassDependency;
import com.caucho.bytecode.JField;
import com.caucho.bytecode.JMethod;
import com.caucho.config.ConfigException;
import com.caucho.java.JavaWriter;
import com.caucho.make.ClassDependency;
import com.caucho.util.CharBuffer;
import com.caucho.util.L10N;
import com.caucho.vfs.PersistentDependency;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Represents a stateful type:
 * embeddable, entity or mapped-superclass.
 */
abstract public class AbstractStatefulType extends AbstractEnhancedType {
  private static final Logger log = Logger.getLogger(AbstractStatefulType.class.getName());
  private static final L10N L = new L10N(AbstractStatefulType.class);

  private boolean _isFieldAccess;

  private ArrayList<AmberField> _fields = new ArrayList<AmberField>();

  private ArrayList<AmberField> _overriddenFields;

  private volatile boolean _isConfigured;

  private ArrayList<PersistentDependency> _dependencies
    = new ArrayList<PersistentDependency>();

  private HashMap<String,String> _completionFields
    = new HashMap<String,String>();

  private Column _discriminator;

  public AbstractStatefulType(AmberPersistenceUnit amberPersistenceUnit)
  {
    super(amberPersistenceUnit);
  }

  /**
   * Set true for field-access.
   */
  public void setFieldAccess(boolean isFieldAccess)
  {
    _isFieldAccess = isFieldAccess;
  }

  /**
   * Set true for field-access.
   */
  public boolean isFieldAccess()
  {
    return _isFieldAccess;
  }

  /**
   * Returns the discriminator.
   */
  public Column getDiscriminator()
  {
    return _discriminator;
  }

  /**
   * Sets the discriminator.
   */
  public void setDiscriminator(Column discriminator)
  {
    _discriminator = discriminator;
  }

  /**
   * Returns the java type.
   */
  public String getJavaTypeName()
  {
    return getInstanceClassName();
  }

  /**
   * Adds a new field.
   */
  public void addField(AmberField field)
  {
    _fields.add(field);
    Collections.sort(_fields, new AmberFieldCompare());
  }

  /**
   * Returns the fields.
   */
  public ArrayList<AmberField> getFields()
  {
    return _fields;
  }

  /**
   * Returns the field with a given name.
   */
  public AmberField getField(String name)
  {
    for (int i = 0; i < _fields.size(); i++) {
      AmberField field = _fields.get(i);

      if (field.getName().equals(name))
        return field;
    }

    return null;
  }

  /**
   * Adds an overridden field.
   */
  public void addOverriddenField(AmberField field)
  {
    if (_overriddenFields == null)
      _overriddenFields = new ArrayList<AmberField>();

    _overriddenFields.add(field);
    Collections.sort(_overriddenFields, new AmberFieldCompare());
  }

  /**
   * Returns the overridden fields.
   */
  public ArrayList<AmberField> getOverriddenFields()
  {
    return _overriddenFields;
  }

  /**
   * Returns the overridden field with a given name.
   */
  public AmberField getOverriddenField(String name)
  {
    if (_overriddenFields == null)
      return null;

    for (int i = 0; i < _overriddenFields.size(); i++) {
      AmberField field = _overriddenFields.get(i);

      if (field.getName().equals(name))
        return field;
    }

    return null;
  }

  /**
   * Sets the bean class.
   */
  public void setBeanClass(JClass beanClass)
  {
    super.setBeanClass(beanClass);

    addDependency(_beanClass);
  }

  /**
   * Adds a dependency.
   */
  public void addDependency(Class cl)
  {
    addDependency(new ClassDependency(cl));
  }

  /**
   * Adds a dependency.
   */
  public void addDependency(JClass cl)
  {
    addDependency(new JClassDependency(cl));
  }

  /**
   * Adds a dependency.
   */
  public void addDependency(PersistentDependency depend)
  {
    if (! _dependencies.contains(depend))
      _dependencies.add(depend);
  }

  /**
   * Gets the dependency.
   */
  public ArrayList<PersistentDependency> getDependencies()
  {
    return _dependencies;
  }

  /**
   * Adds a new completion field.
   */
  public void addCompletionField(String name)
  {
    _completionFields.put(name, name);
  }

  /**
   * Returns true if and only if it has the completion field.
   */
  public boolean containsCompletionField(String completionField)
  {
    return _completionFields.containsKey(completionField);
  }

  /**
   * Remove all completion fields.
   */
  public void removeAllCompletionFields()
  {
    _completionFields.clear();
  }

  /**
   * Set true if configured.
   */
  public boolean startConfigure()
  {
    synchronized (this) {
      if (_isConfigured)
        return false;

      _isConfigured = true;

      return true;
    }
  }

  /**
   * Initialize the type.
   */
  public void init()
    throws ConfigException
  {
  }

  /**
   * Converts the value.
   */
  public String generateCastFromObject(String value)
  {
    return "((" + getInstanceClassName() + ") " + value + ")";
  }

  /**
   * Generates a string to load the field.
   */
  public int generateLoad(JavaWriter out, String rs,
                          String indexVar, int index, int loadGroupIndex)
    throws IOException
  {
    if (loadGroupIndex == 0 && _discriminator != null)
      index++;

    // jpa/0ge2
    ArrayList<AmberField> fields = getOverriddenFields();

    for (int i = 0; i < 2; i++) {
      if (fields == null) {
        fields = getFields();
        continue;
      }

      for (int j = 0; j < fields.size(); j++) {
        AmberField field = fields.get(j);

        if (field.getLoadGroupIndex() == loadGroupIndex)
          index = field.generateLoad(out, rs, indexVar, index);
      }

      fields = getFields();
    }

    return index;
  }

  /**
   * Generates the select clause for a load.
   */
  abstract public String generateLoadSelect(Table table, String id);

  /**
   * Generates the select clause for a load.
   */
  public String generateLoadSelect(Table table,
                                   String id,
                                   int loadGroup)
  {
    return generateLoadSelect(table, id, loadGroup, false);
  }

  /**
   * Generates the select clause for a load.
   */
  public String generateLoadSelect(Table table,
                                   String id,
                                   int loadGroup,
                                   boolean hasSelect)
  {
    CharBuffer cb = CharBuffer.allocate();

    // jpa/0ge2
    ArrayList<AmberField> fields = getOverriddenFields();

    for (int i = 0; i < 2; i++) {
      if (fields == null) {
        fields = getFields();
        continue;
      }

      for (int j = 0; j < fields.size(); j++) {
        AmberField field = fields.get(j);

        if (field.getLoadGroupIndex() != loadGroup)
          continue;

        String propSelect = field.generateLoadSelect(table, id);

        if (propSelect == null)
          continue;

        if (hasSelect)
          cb.append(", ");
        hasSelect = true;

        cb.append(propSelect);
      }

      fields = getFields();
    }

    if (cb.length() == 0)
      return null;
    else
      return cb.close();
  }

  /**
   * Generates the foreign delete
   */
  public void generateInvalidateForeign(JavaWriter out)
    throws IOException
  {
    for (AmberField field : getFields()) {
      field.generateInvalidateForeign(out);
    }
  }

  /**
   * Generates any expiration code.
   */
  public void generateExpire(JavaWriter out)
    throws IOException
  {
    for (AmberField field : getFields()) {
      field.generateExpire(out);
    }
  }

  /**
   * Gets a matching getter.
   */
  public JMethod getGetter(String name)
  {
    return getGetter(_beanClass, name);
  }

  /**
   * Gets a matching getter.
   */
  public static JMethod getGetter(JClass cl, String name)
  {
    JMethod []methods = cl.getMethods();

    for (int i = 0; i < methods.length; i++) {
      JClass []param = methods[i].getParameterTypes();
      String methodName = methods[i].getName();

      if (name.equals(methodName) && param.length == 0)
        return methods[i];
    }

    cl = cl.getSuperClass();

    if (cl != null)
      return getGetter(cl, name);
    else
      return null;
  }

  /**
   * Gets a matching getter.
   */
  public static JField getField(JClass cl, String name)
  {
    JField []fields = cl.getDeclaredFields();

    for (int i = 0; i < fields.length; i++) {
      if (name.equals(fields[i].getName()))
        return fields[i];
    }

    cl = cl.getSuperClass();

    if (cl != null)
      return getField(cl, name);
    else
      return null;
  }

  /**
   * Gets a matching getter.
   */
  public static JMethod getSetter(JClass cl, String name)
  {
    JMethod []methods = cl.getMethods();

    for (int i = 0; i < methods.length; i++) {
      JClass []param = methods[i].getParameterTypes();
      String methodName = methods[i].getName();

      if (name.equals(methodName) && param.length == 1)
        return methods[i];
    }

    cl = cl.getSuperClass();

    if (cl != null)
      return getSetter(cl, name);
    else
      return null;
  }

  /**
   * Returns the load mask generated on create.
   */
  public long getCreateLoadMask(int group)
  {
    long mask = 0;

    for (int i = 0; i < _fields.size(); i++) {
      mask |= _fields.get(i).getCreateLoadMask(group);
    }

    return mask;
  }

  /**
   * Printable version of the entity.
   */
  public String toString()
  {
    return "AbstractStatefulType[" + _beanClass.getName() + "]";
  }
}
