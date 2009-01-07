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

package com.caucho.security;

import com.caucho.cluster.Cache;
import com.caucho.cluster.AbstractCache;
import com.caucho.cluster.TriplicateCache;
import com.caucho.util.LruCache;

import java.util.logging.Logger;

import java.security.Principal;

import javax.annotation.PostConstruct;

/**
 * Cluster-based cache for single-signon.
 *
 * @since Resin 4.0.0
 */
@javax.webbeans.ApplicationScoped
@com.caucho.config.Service
public class ClusterSingleSignon implements SingleSignon {
  private final static Logger log
    = Logger.getLogger(MemorySingleSignon.class.getName());

  private AbstractCache _cache;

  public ClusterSingleSignon()
  {
    _cache = new TriplicateCache("resin:single-signon");
  }

  public ClusterSingleSignon(String name)
  {
    this();

    setName(name);

    init();
  }

  public void setName(String name)
  {
    _cache.setName("resin:single-signon:" + name);
  }
  
  /**
   * Initialize the single signon.
   */
  @PostConstruct
  public void init()
  {
    _cache.init();
  }
  
  /**
   * Returns any saved single signon entry.
   */
  public Principal get(String id)
  {
    return (Principal) _cache.get(id);
  }
  
  /**
   * Adds a principal to the cache
   *
   * @return the logged in principal on success, null on failure.
   */
  public void put(String id, Principal user)
  {
    _cache.put(id, user);
  }
  
  /**
   * Removes a principal from the single-signon
   *
   * @return the logged in principal on success, null on failure.
   */
  public boolean remove(String id)
  {
    return _cache.remove(id);
  }
}
