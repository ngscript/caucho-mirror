/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
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

package com.caucho.server.webapp;

import com.caucho.server.connection.*;
import com.caucho.util.L10N;
import com.caucho.vfs.WriteStream;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;

/**
 * Internal response for an include() or forward()
 */
class ForwardResponse extends CauchoResponseWrapper
{
  private static final L10N L = new L10N(ForwardResponse.class);

  ForwardResponse()
  {
  }

  ForwardResponse(HttpServletResponse response)
  {
    super(response);
  }

  void startRequest()
  {
  }

  void finishRequest()
    throws IOException
  {
    // server/106f
    AbstractResponseStream stream = getResponseStream();

    // ioc/0310 vs server/12b2

    /*
    if (stream != null)
      stream.close();
    if (stream != null)
      stream.finish();
    */
    if (stream != null)
      stream.close();
  }
}
