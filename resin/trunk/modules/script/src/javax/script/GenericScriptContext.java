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

package javax.script;

import java.io.Writer;
import java.io.Reader;

/**
 * Basic implementation of a script context.
 */
public class GenericScriptContext implements ScriptContext {
  protected Namespace engineScope;
  protected Namespace globalScope;
  
  protected Reader reader;
  protected Writer writer;
  protected Writer errorWriter;

  /**
   * Sets the namespace with the given scope.
   */
  public void setNamespace(Namespace namespace, int scope)
  {
    switch (scope) {
    case ENGINE_SCOPE:
      this.engineScope = namespace;
      break;
    case GLOBAL_SCOPE:
      this.globalScope = namespace;
      break;
    default:
      throw new IllegalArgumentException("scope '" + scope + " must either be ENGINE_SCOPE or GLOBAL_SCOPE.");
    }
  }

  /**
   * Gets the namespace with the given scope.
   */
  public Namespace getNamespace(int scope)
  {
    switch (scope) {
    case ENGINE_SCOPE:
      return this.engineScope;
    case GLOBAL_SCOPE:
      return this.globalScope;
    default:
      throw new IllegalArgumentException("scope '" + scope + " must either be ENGINE_SCOPE or GLOBAL_SCOPE.");
    }
  }

  /**
   * Returns the attribute value.
   */
  public Object getAttribute(String name)
  {
    Object v = this.engineScope.get(name);

    if (v != null)
      return v;

    v = this.globalScope.get(name);

    return v;
  }

  /**
   * Returns the attribute value.
   */
  public int getAttributesScope(String name)
  {
    Object v = this.engineScope.get(name);

    if (v != null)
      return ENGINE_SCOPE;

    v = this.globalScope.get(name);

    if (v != null)
      return GLOBAL_SCOPE;

    return -1;
  }

  /**
   * Returns the attribute value for the given scope.
   */
  public Object getAttribute(String name, int scope)
  {
    return getNamespace(scope).get(name);
  }

  /**
   * Removes the attribute value for the given scope.
   */
  public Object removeAttribute(String name, int scope)
  {
    return getNamespace(scope).remove(name);
  }

  /**
   * Sets the attribute value for the given scope.
   */
  public void setAttribute(String name, Object value, int scope)
  {
    getNamespace(scope).put(name, value);
  }

  /**
   * Returns the reader.
   */
  public Reader getReader()
  {
    return this.reader;
  }

  /**
   * Sets the reader.
   */
  public void setReader(Reader reader)
  {
    this.reader = reader;
  }

  /**
   * Returns the writer.
   */
  public Writer getWriter()
  {
    return this.writer;
  }

  /**
   * Sets the writer.
   */
  public void setWriter(Writer writer)
  {
    this.writer = writer;
  }

  /**
   * Returns the error writer.
   */
  public Writer getErrorWriter()
  {
    return this.errorWriter;
  }

  /**
   * Sets the error writer.
   */
  public void setErrorWriter(Writer writer)
  {
    this.writer = writer;
  }
}

