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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.server.deploy;

import java.io.IOException;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Set;
import java.util.Iterator;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.caucho.util.L10N;

import com.caucho.log.Log;

import com.caucho.vfs.Path;

import com.caucho.loader.Environment;

import com.caucho.make.Dependency;
import com.caucho.make.CachedDependency;

import com.caucho.lifecycle.Lifecycle;

/**
 * A container of deploy objects.
 */
public class DeployContainer<E extends DeployController>
  extends CachedDependency
  implements Dependency {
  private static final Logger log = Log.open(DeployContainer.class);
  private static final L10N L = new L10N(DeployContainer.class);

  private DeployListGenerator<E> _deployList = new DeployListGenerator<E>(this);

  private ArrayList<E> _controllerList = new ArrayList<E>();

  private final Lifecycle _lifecycle = new Lifecycle();

  /**
   * Creates the deploy container.
   */
  public DeployContainer()
  {
    setCheckInterval(Environment.getDependencyCheckInterval());
  }
  
  /**
   * Adds a deploy.
   */
  public void add(DeployGenerator<E> deploy)
  {
    Set<String> names = new TreeSet<String>();
    deploy.fillDeployedKeys(names);

    _deployList.add(deploy);

    update(names);
  }
  
  /**
   * Removes a deploy.
   */
  public void remove(DeployGenerator<E> deploy)
  {
    Set<String> names = new TreeSet<String>();
    deploy.fillDeployedKeys(names);

    _deployList.remove(deploy);

    update(names);
  }

  /**
   * Returns true if the deployment has modified.
   */
  public boolean isModifiedImpl()
  {
    return _deployList.isModified();
  }

  /**
   * Forces updates.
   */
  public void update()
  {
    _deployList.update();
  }

  /**
   * Redeploys modified deployments.
   */
  public void redeployIfModified()
  {
    _deployList.redeployIfModified();
  }

  /**
   * Initialize the container.
   */
  public void init()
  {
    if (! _lifecycle.toInit())
      return;
  }

  /**
   * Start the container.
   */
  public void start()
  {
    init();

    if (! _lifecycle.toActive())
      return;

    _deployList.start();

    for (int i = 0; i < _controllerList.size(); i++) {
      E entry = _controllerList.get(i);

      entry.startOnInit();
    }
  }

  /**
   * Returns the matching entry.
   */
  public E findController(String name)
  {
    E controller = findDeployedController(name);

    if (controller != null)
      return controller;

    controller = generateController(name);

    return controller;
  }

  /**
   * Returns the deployed entries.
   */
  public ArrayList<E> getEntries()
  {
    ArrayList<E> list = new ArrayList<E>();

    synchronized (_controllerList) {
      list.addAll(_controllerList);
    }

    return list;
  }

  /**
   * Updates all the names.
   */
  private void update(Set<String> names)
  {
    Iterator<String> iter = names.iterator();
    while (iter.hasNext()) {
      String name = iter.next();

      update(name);
    }
  }

  /**
   * Callback from the DeployGenerator when the deployment changes.
   * <code>update</code> is only called when a deployment is added
   * or removed, e.g. with a new .war file.
   *
   * The entry handles its own internal changes, e.g. a modification to
   * a web.xml file.
   */
  public E update(String name)
  {
    E newEntry = updateImpl(name);
    
    if (_lifecycle.isActive() && newEntry != null)
      newEntry.startOnInit();

    return newEntry;
  }

  /**
   * Callback from the DeployGenerator when the deployment changes.
   * <code>update</code> is only called when a deployment is added
   * or removed, e.g. with a new .war file.
   *
   * The entry handles its own internal changes, e.g. a modification to
   * a web.xml file.
   */
  E updateImpl(String name)
  {
    E oldEntry = null;

    synchronized (_controllerList) {
      oldEntry = findDeployedController(name);
    }

    E newEntry = _deployList.generateController(name);
    
    if (oldEntry != null && oldEntry != newEntry) {
      if (oldEntry != null)
	_controllerList.remove(oldEntry);
      
      oldEntry.destroy();
    }

    // server/102u
    if (newEntry != null && ! _controllerList.contains(newEntry)) {
      newEntry.setDeployContainer(this);
      _controllerList.add(newEntry);

      init(newEntry);
    }

    return newEntry;
  }

  /**
   * Called to explicitly remove an entry from the cache.
   */
  public void remove(String name)
  {
    E oldEntry = null;

    synchronized (_controllerList) {
      oldEntry = findDeployedController(name);

      if (oldEntry != null)
	_controllerList.remove(oldEntry);
    }

    if (oldEntry != null)
      oldEntry.destroy();
  }

  /**
   * Generates the entry.
   */
  private E generateController(String name)
  {
    E newEntry = _deployList.generateController(name);

    if (newEntry == null)
      return null;

    // the new entry might already be generated by another thread
    synchronized (_controllerList) {
      E entry = findDeployedController(name);

      if (entry != null)
	return entry;

      newEntry.setDeployContainer(this);
      _controllerList.add(newEntry);
    }

    init(newEntry);

    return newEntry;
  }

  private void init(E entry)
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      thread.setContextClassLoader(entry.getParentClassLoader());
      
      entry.init();
      
      entry.deployEntry();
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  /**
   * Returns an already deployed entry.
   */
  private E findDeployedController(String name)
  {
    synchronized (_controllerList) {
      for (int i = 0; i < _controllerList.size(); i++) {
	E entry = _controllerList.get(i);

	if (entry.isNameMatch(name)) {
	  return entry;
	}
      }
    }

    return null;
  }
  
  /**
   * Closes the stop.
   */
  public void stop()
  {
    if (! _lifecycle.toStop())
      return;

    // _deployList.stop();
    
    ArrayList<E> entries = new ArrayList<E>(_controllerList);

    for (int i = 0; i < entries.size(); i++)
      entries.get(i).stop();
  }
  
  /**
   * Closes the deploys.
   */
  public void destroy()
  {
    stop();
    
    if (! _lifecycle.toDestroy())
      return;
    
    _deployList.destroy();
    _controllerList.clear();
  }

  public String toString()
  {
    return "DeployContainer$" + System.identityHashCode(this) + "[]";
  }
}
