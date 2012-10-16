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

package com.caucho.env.actor;


/**
 * Ring-based memory queue processed by a single worker.
 */
abstract public class AbstractWorkerQueue<T> implements ActorProcessor<T> 
{
  private final ValueActorQueue<T> _queueConsumer;
  
  public AbstractWorkerQueue(int size)
  {
    _queueConsumer = new ValueActorQueue<T>(size, this);
  }
  
  public final boolean isEmpty()
  {
    return _queueConsumer.isEmpty();
  }
  
  public final int getSize()
  {
    return _queueConsumer.getSize();
  }
  
  public final boolean offer(T value)
  {
    _queueConsumer.offer(value);
    
    return true;
  }
  
  public final void wake()
  {
    _queueConsumer.wake();
  }
  
  @Override
  public String getThreadName()
  {
    long id = Thread.currentThread().getId();
    
    return _queueConsumer.toString() + "-" + id;
  }
  
  @Override
  abstract public void process(T value);
  
  @Override
  public void onProcessComplete()
  {
  }
}