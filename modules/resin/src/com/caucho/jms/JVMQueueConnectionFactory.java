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

package com.caucho.jms;

import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.JMSException;

import com.caucho.jms.session.QueueConnectionImpl;
  
/**
 * A sample queue connection factory.
 */
public class JVMQueueConnectionFactory extends ConnectionFactoryImpl
  implements QueueConnectionFactory  {
  public JVMQueueConnectionFactory()
  {
  }

  /**
   * Creates a new queue connection
   */
  public QueueConnection createQueueConnection()
    throws JMSException
  {
    return createQueueConnection(null, null);
  }

  /**
   * Creates a new queue connection
   *
   * @param username the username to authenticate with the server.
   * @param password the password to authenticate with the server.
   *
   * @return the created connection
   */
  public QueueConnection createQueueConnection(String username,
                                               String password)
    throws JMSException
  {
    authenticate(username, password);
    
    QueueConnectionImpl conn = new QueueConnectionImpl(this);

    addConnection(conn);
    
    return conn;
  }
}
