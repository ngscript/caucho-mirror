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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.server.security;

import java.io.*;
import java.util.*;
import java.security.*;

import javax.servlet.http.*;
import javax.servlet.*;

import com.caucho.util.*;
import com.caucho.vfs.*;
import com.caucho.server.webapp.Application;
import com.caucho.server.host.Host;

public class TransportConstraint extends AbstractConstraint {
  private String _transport;

  public TransportConstraint()
  {
  }

  public TransportConstraint(String transport)
  {
    _transport = transport;
  }
  
  public void setTransportGuarantee(String transportGuarantee)
  {
    _transport = transportGuarantee;
  }
  
  /**
   * Returns true if any cache needs to be private.
   */
  public boolean isPrivateCache()
  {
    return false;
  }

  /**
   * Returns true if the user is authorized for the resource.
   */
  public boolean isAuthorized(HttpServletRequest request,
                              HttpServletResponse response,
                              ServletContext application)
    throws ServletException, IOException
  {
    if (_transport == null)
      return true;
    
    if (request.isSecure())
      return true;

    Application app = (Application) application;
    Host host = (Host) app.getParent();
    String secureHost = host.getSecureHostName();

    if (secureHost != null) {
      String url = ("https://" + secureHost + app.getContextPath() +
		    request.getServletPath());

      if (request.getPathInfo() != null)
	url += request.getPathInfo();
      if (request.getQueryString() != null)
	url += request.getQueryString();

      response.sendRedirect(url);
      return false;
    }
    
    String url = request.getRequestURL().toString();

    if (url.startsWith("http:")) {
      url = "https:" + url.substring(5);
      String queryString = request.getQueryString();
      if (queryString != null)
        response.sendRedirect(url + "?" + queryString);
      else
        response.sendRedirect(url);
      return false;
    }
    
    response.sendError(HttpServletResponse.SC_FORBIDDEN, null);

    return false;
  }
}
