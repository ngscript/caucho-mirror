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
 * @author Adam Megacz
 */

package com.caucho.jaxb.skeleton;
import com.caucho.jaxb.*;
import javax.xml.bind.*;
import javax.xml.namespace.*;
import javax.xml.stream.*;
import java.util.*;

import java.lang.reflect.*;
import java.io.*;

import com.caucho.vfs.WriteStream;

/**
 * a Map property
 */
public class MapProperty extends Property {

  public MapProperty(Accessor a)
  {
    super(a);
  }
  
  public Object read(Unmarshaller u, XMLStreamReader in)
    throws IOException, XMLStreamException, JAXBException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void write(Marshaller m, XMLStreamWriter out, Object obj)
    throws IOException, XMLStreamException, JAXBException
  {
    /*
    out.print('<');
    out.print(fieldName);
    out.print('>');
    
    //StringProperty.escapify((String)obj, out);
    
    out.print("</");
    out.print(fieldName);
    out.print(">");
    */
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void generateSchema(XMLStreamWriter out)
    throws XMLStreamException
  {
    throw new UnsupportedOperationException();
  }

  protected String getSchemaType()
  {
    throw new UnsupportedOperationException();
  }

  protected boolean isPrimitiveType()
  {
    return false;
  }

  protected boolean isXmlPrimitiveType()
  {
    return false;
  }
}


