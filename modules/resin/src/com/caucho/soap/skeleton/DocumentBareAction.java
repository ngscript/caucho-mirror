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
 * @author Emil Ong
 */

package com.caucho.soap.skeleton;

import com.caucho.jaxb.skeleton.Property;
import com.caucho.jaxb.JAXBContextImpl;
import com.caucho.util.L10N;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Invokes a SOAP request on a Java POJO method.
 *
 * This class handles the document-literal bare (i.e. non-wrapped) style
 * which JAX-WS maps to methods of at most one input and one output 
 * argument.  Non-void return values count as an output argument and
 * INOUT arguments count as both one input and one output.
 */
public class DocumentBareAction extends AbstractAction {
  private final static Logger log = 
    Logger.getLogger(DocumentBareAction.class.getName());
  private static final L10N L = new L10N(DocumentBareAction.class);

  public DocumentBareAction(Method method, Method eiMethod,
                            JAXBContextImpl jaxbContext, 
                            String targetNamespace,
                            Marshaller marshaller,
                            Unmarshaller unmarshaller)
    throws JAXBException, WebServiceException
  {
    super(method, eiMethod, 
          jaxbContext, targetNamespace, 
          marshaller, unmarshaller);

    if (_bodyInputs + _headerInputs > 1)
      throw new WebServiceException(L.l("Document bare methods may not have more than one input argument"));

    if (_bodyOutputs + _headerOutputs > 1)
      throw new WebServiceException(L.l("Document bare methods may not have more than one output argument (including the return value)"));

    //
    // Fix the argument/response names
    //

    // XXX header args/no args
    // XXX check for input versus output
    
    // XXX check that @WebParam is not set on the argument (and for some
    // reason set the name to "arg0")
    if (_bodyArgs.length == 1) {
      if ("arg0".equals(_bodyArgs[0].getName().getLocalPart()))
        _bodyArgs[0].setName(new QName(_targetNamespace, _operationName));
      else
        _operationName = _bodyArgs[0].getName().getLocalPart();
    }

    if (_returnMarshal != null) {
      WebResult webResult = _method.getAnnotation(WebResult.class);

      if (webResult == null && eiMethod != null)
        webResult = eiMethod.getAnnotation(WebResult.class);

      if (webResult == null || "".equals(webResult.name()))
        _returnMarshal.setName(new QName(_targetNamespace, _responseName));
    }
  }

  protected void writeMethodInvocation(XMLStreamWriter out, Object []args)
    throws IOException, XMLStreamException, JAXBException
  {
    for (int i = 0; i < _bodyArgs.length; i++)
      _bodyArgs[i].serializeCall(out, args);
  }

  protected Object[] readMethodInvocation(XMLStreamReader in)
    throws IOException, XMLStreamException, JAXBException
  {
    Object[] args = new Object[_arity];

    if (_bodyArgs.length == 0) {
      while (in.getEventType() != in.END_ELEMENT)
        in.nextTag();

      in.nextTag();
    }
    else {
      for (int i = 0; i < _bodyArgs.length; i++) {
        // while loop for arrays/lists
        while (in.getEventType() == in.START_ELEMENT &&
               _bodyArgs[i].getName().equals(in.getName()))
          _bodyArgs[i].deserializeCall(in, args);
      }

    }

    return args;
  }

  protected void writeResponse(XMLStreamWriter out, Object value, Object[] args)
    throws IOException, XMLStreamException, JAXBException
  {
    if (_returnMarshal != null)
      _returnMarshal.serializeReply(out, value);

    for (int i = 0; i < _bodyArgs.length; i++)
      _bodyArgs[i].serializeReply(out, args);
  }

  protected Object readResponse(XMLStreamReader in, Object []args)
    throws IOException, XMLStreamException, JAXBException, Throwable
  {
    Object ret = null;

    in.nextTag();
    in.require(XMLStreamReader.START_ELEMENT, null, "Envelope");

    in.nextTag();
    in.require(XMLStreamReader.START_ELEMENT, null, null);

    if ("Header".equals(in.getLocalName())) {
      while (in.nextTag() == XMLStreamReader.START_ELEMENT) {
        String tagName = in.getLocalName();

        ParameterMarshal marshal = _headerArguments.get(tagName);

        if (marshal != null)
          marshal.deserializeReply(in, args);
        else {
          int depth = 1;

          while (depth > 0) {
            switch (in.nextTag()) {
              case XMLStreamReader.START_ELEMENT:
                depth++;
                break;
              case XMLStreamReader.END_ELEMENT:
                depth--;
                break;
              default:
                throw new IOException("expected </Header>");
            }
          }
        }
      }

      in.require(XMLStreamReader.END_ELEMENT, null, "Header");
    }

    in.require(XMLStreamReader.START_ELEMENT, null, "Body");

    in.nextTag();

    if (in.getEventType() == XMLStreamReader.START_ELEMENT &&
        "Fault".equals(in.getLocalName())) {
      Throwable fault = readFault(in);

      if (fault == null)
        throw new WebServiceException(); // XXX

      throw fault;
    }

    if (_returnMarshal != null)
      ret = _returnMarshal.deserializeReply(in, ret);

    for (int i = 0; i < _bodyArgs.length; i++)
      _bodyArgs[i].deserializeReply(in, args);

    in.require(XMLStreamReader.END_ELEMENT, null, "Body");

    in.nextTag();
    in.require(XMLStreamReader.END_ELEMENT, null, "Envelope");

    return ret;
  }
}

