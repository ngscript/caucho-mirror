/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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

package com.caucho.mqueue.amqp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import com.caucho.network.listen.Protocol;
import com.caucho.network.listen.ProtocolConnection;
import com.caucho.network.listen.SocketLink;
import com.caucho.vfs.TempBuffer;
import com.caucho.vfs.WriteStream;

public class AmqpStreamWriter extends AmqpBaseWriter {
  private OutputStream _os;
  
  public AmqpStreamWriter(OutputStream os)
  {
    _os = os;
  }
  
  @Override
  public void write(int ch)
    throws IOException
  {
    _os.write(ch);
  }
  
  @Override
  public void flush()
    throws IOException
  {
    _os.flush();
  }

  /* (non-Javadoc)
   * @see com.caucho.mqueue.amqp.AmqpBaseWriter#getOffset()
   */
  @Override
  public int getOffset()
  {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see com.caucho.mqueue.amqp.AmqpBaseWriter#writeByte(int, int)
   */
  @Override
  public void writeByte(int offset, int value)
  {
    // TODO Auto-generated method stub
    
  }
}