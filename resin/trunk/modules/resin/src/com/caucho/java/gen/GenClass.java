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

package com.caucho.java.gen;

import java.lang.reflect.Method;

import java.util.ArrayList;

import java.io.IOException;

import com.caucho.util.L10N;

import com.caucho.java.JavaWriter;

/**
 * Basic class generation.
 */
public class GenClass extends BaseClass {
  private static L10N L = new L10N(GenClass.class);

  private String _packageName;
  private String _fullClassName;

  private ArrayList<String> _importList = new ArrayList<String>();

  /**
   * Creates the base class
   */
  public GenClass(String fullClassName)
  {
    _fullClassName = fullClassName;

    int p = fullClassName.lastIndexOf('.');

    if (p > 0) {
      _packageName = fullClassName.substring(0, p);
      setClassName(fullClassName.substring(p + 1));
    }
    else {
      throw new IllegalArgumentException(L.l("Class '{0}' must belong to a package.",
					     fullClassName));
    }
  }

  /**
   * Returns the full class name.
   */
  public String getFullClassName()
  {
    return _fullClassName;
  }

  /**
   * Returns the package name
   */
  public String getPackageName()
  {
    return _packageName;
  }

  /**
   * Adds an import package.
   */
  public void addImport(String importName)
  {
    if (! _importList.contains(importName))
      _importList.add(importName);
  }

  /**
   * Generates the class.
   */
  public void generate(JavaWriter out)
    throws IOException
  {
    generateTopComment(out);

    if (_packageName != null) {
      out.println();
      out.println("package " + _packageName + ";");
    }

    if (_importList.size() > 0) {
      out.println();

      for (int i = 0; i < _importList.size(); i++) {
	out.println("import " + _importList.get(i) + ";");
      }
    }

    out.println();

    super.generate(out);
  }

  /**
   * Generates the top comment.
   */
  protected void generateTopComment(JavaWriter out)
    throws IOException
  {
    out.println("/*");
    out.println(" * Generated by " + com.caucho.Version.FULL_VERSION);
    out.println(" */");
  }
}
