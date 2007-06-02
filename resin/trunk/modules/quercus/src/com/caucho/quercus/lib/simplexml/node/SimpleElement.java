/*
 * Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.quercus.lib.simplexml.node;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.Value;
import com.caucho.vfs.WriteStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Represents a SimpleXML element node (i.e. not attribute or text).
 */
public class SimpleElement extends SimpleNode
{
  public SimpleElement()
  {
    _children = new ArrayList<SimpleNode>();
    _elementList = new ArrayList<SimpleElement>();
    
    _elementMap = new HashMap<String,SimpleElement>();
    _attributeMap = new HashMap<String,SimpleAttribute>();
  }

  public SimpleElement(String name, String namespace)
  {
    this();
    
    setName(name);
    setNamespace(namespace);
  }
  
  @Override
  public boolean isElement()
  {
    return true;
  }
  
  @Override
  public int getObjectSize()
  {
    int size = getElementMap().size();
    
    if (size == 0 && getChildren().size() > 0)
      size++;
    
    if (getAttributes().size() > 0)
      size++;
    
    return size;
  }
  
  @Override
  protected void toXMLImpl(StringBuilder sb)
  {
    sb.append("<");
    
    if (getPrefix() != null)
      sb.append(getPrefix() + ":");
    
    sb.append(getName());

    if (getNamespace() != null) {  
      sb.append(" xmlns");
      
      if (getPrefix() != null) {
        sb.append(":");
        sb.append(getPrefix());
      }

      sb.append("=\"");
      sb.append(getNamespace());
      sb.append("\"");
    }
    
    // add attributes, if any
    HashMap<String,SimpleAttribute> attrs = getAttributes();

    for (Map.Entry<String,SimpleAttribute> entry : attrs.entrySet()) {
      entry.getValue().toXMLImpl(sb);
    }

    // recurse through children, if any
    ArrayList<SimpleNode> children = getChildren();

    if (children.size() == 0) {
      sb.append("/>");
      return;
    }

    sb.append(">");

    // there are children
    for (SimpleNode child : children) {
      child.toXMLImpl(sb);
    }

    // add closing tag
    sb.append("</");
    
    if (getPrefix() != null)
      sb.append(getPrefix() + ":");
    
    sb.append(getName());
    sb.append(">");
  }
  
  @Override
  public void varDumpImpl(Env env,
                          WriteStream out,
                          int depth,
                          IdentityHashMap<Value, String> valueSet)
    throws IOException
  {
    HashMap<String,SimpleAttribute> attrMap = getAttributes();
    int size = attrMap.size();
    
    if (size > 0) {
      printDepth(out, 2 * depth);
      out.println("[\"@attributes\"]=>");
      
      printDepth(out, 2 * depth);
      out.println("array(" + size + ") {");
      
      for (Map.Entry<String,SimpleAttribute> entry : attrMap.entrySet()) {
        entry.getValue().varDumpNested(env, out, depth + 1, valueSet);
      }
      
      printDepth(out, 2 * depth);
      out.println('}');
    }
    
    HashMap<String,SimpleElement> elementMap = getElementMap();
    size = elementMap.size();
    
    if (size > 0) {
      for (Map.Entry<String,SimpleElement> entry : elementMap.entrySet()) {
        printDepth(out, 2 * depth);
        out.println("[\"" + entry.getKey() + "\"]=>");
        
        entry.getValue().varDumpNested(env, out, depth, valueSet);
      }
    }
    else if (getChildren().size() > 0) {
      printDepth(out, 2 * depth);
      out.println("[0]=>");
      
      getChildren().get(0).varDumpNested(env, out, depth, valueSet);
    }
  }
  
  @Override
  void varDumpNested(Env env,
                   WriteStream out,
                   int depth,
                   IdentityHashMap<Value, String> valueSet)
    throws IOException
  {
    // don't care even if there are attributes
    if (getElementList().size() == 0 && getChildren().size() > 0) {

      getChildren().get(0).varDumpNested(env, out, depth, valueSet);
      return;
    }

    int size = getObjectSize();

    printDepth(out, 2 * depth);
    out.println("object(SimpleXMLElement) (" + size + ") {");
    
    varDumpImpl(env, out, depth + 1, valueSet);
    printDepth(out, 2 * depth);
    
    out.println('}');
  }
  
  @Override
  public void printRImpl(Env env,
                         WriteStream out,
                         int depth,
                         IdentityHashMap<Value, String> valueSet)
    throws IOException
  {
    HashMap<String,SimpleAttribute> attrMap = getAttributes();
    int size = attrMap.size();
    
    if (size > 0) {
      printDepth(out, 4 * depth);
      out.print("[@attributes] => ");
      out.println("Array");
      
      printDepth(out, 4 * (depth + 1));
      out.println('(');
      
      for (Map.Entry<String,SimpleAttribute> entry : attrMap.entrySet()) {
        entry.getValue().printRNested(env, out, depth + 2, valueSet);
      }
      
      printDepth(out, 4 * (depth + 1));
      out.println(')');
      out.println();
    }
    
    HashMap<String,SimpleElement> elementMap = getElementMap();
    size = elementMap.size();
    
    if (size > 0) {
      for (Map.Entry<String,SimpleElement> entry : elementMap.entrySet()) {
        printDepth(out, 4 * depth);
        out.print("[" + entry.getKey() + "] => ");
        
        entry.getValue().printRNested(env, out, depth + 1, valueSet);
      }
    }
    else if (getChildren().size() > 0) {
      printDepth(out, 4 * depth);
      out.print("[0] => ");
      
      getChildren().get(0).printRNested(env, out, depth, valueSet);
    }
  }
  
  @Override
  void printRNested(Env env,
                    WriteStream out,
                    int depth,
                    IdentityHashMap<Value, String> valueSet)
    throws IOException
  {
    if (getElementList().size() == 0 && getChildren().size() > 0) {
      getChildren().get(0).printRNested(env, out, depth, valueSet);
      
      return;
    }
    
    out.println("SimpleXMLElement Object");
    
    printDepth(out, 4 * depth);
    out.println('(');
    
    printRImpl(env, out, depth + 1, valueSet);
    
    printDepth(out, 4 * depth);
    
    out.println(')');
    out.println();
  }
  
  public String toString()
  {
    if (getElementList().size() == 0 && getChildren().size() > 0)
      return getChildren().get(0).toString();
    else
      return "";
  }
}
