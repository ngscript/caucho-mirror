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

package com.caucho.message.common;

import com.caucho.message.MessageReceiver;
import com.caucho.message.MessageReceiverFactory;
import com.caucho.message.SettleMode;
import com.caucho.message.SettleTime;
import com.caucho.util.L10N;

/**
 * local connection to the message store
 */
abstract public class AbstractMessageReceiverFactory implements MessageReceiverFactory {
  private static final L10N L = new L10N(AbstractMessageReceiverFactory.class);
  
  private String _address;
  private int _prefetch;
  private SettleMode _settleMode = SettleMode.ALWAYS;
  private SettleTime _settleTime = SettleTime.QUEUE_REMOVE;

  @Override
  public MessageReceiverFactory setAddress(String address)
  {
    _address = address;
    
    return this;
  }

  @Override
  public String getAddress()
  {
    return _address;
  }

  @Override
  public int getPrefetch()
  {
    return _prefetch;
  }

  @Override
  public MessageReceiverFactory setPrefetch(int prefetch)
  {
    _prefetch = prefetch;

    return this;
  }

  @Override
  public MessageReceiverFactory setSettleMode(SettleMode settleMode)
  {
    _settleMode = settleMode;
    
    return this;
  }

  @Override
  public SettleMode getSettleMode()
  {
    return _settleMode;
  }

  @Override
  public MessageReceiverFactory setSettleTime(SettleTime settleTime)
  {
    _settleTime = settleTime;
    
    return this;
  }

  @Override
  public SettleTime getSettleTime()
  {
    return _settleTime;
  }

  @Override
  abstract public MessageReceiver<?> build();
  
  @Override 
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _address + "]";
  }
}
