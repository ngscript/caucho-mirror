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

package com.caucho.server.host;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Iterator;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.caucho.log.Log;

import com.caucho.vfs.Path;

import com.caucho.el.EL;

import com.caucho.config.ConfigException;

import com.caucho.config.types.RawString;

import com.caucho.server.deploy.ExpandDeployGenerator;
import com.caucho.server.deploy.DeployContainer;

/**
 * The generator for the host deploy
 */
public class HostExpandDeployGenerator extends ExpandDeployGenerator<HostController> {
  private static final Logger log = Log.open(HostExpandDeployGenerator.class);

  private HostContainer _container;

  private ArrayList<HostConfig> _hostDefaults = new ArrayList<HostConfig>();

  private String _hostName;

  /**
   * Creates the new host deploy.
   */
  public HostExpandDeployGenerator(DeployContainer<HostController> container,
			  HostContainer hostContainer)
  {
    super(container);
    
    _container = hostContainer;
  }

  /**
   * Gets the host container.
   */
  public HostContainer getContainer()
  {
    return _container;
  }

  /**
   * Sets the host name.
   */
  public void setHostName(RawString name)
  {
    _hostName = name.getValue();
  }

  /**
   * Gets the host name.
   */
  public String getHostName()
  {
    return _hostName;
  }

  /**
   * Sets true for a lazy-init.
   */
  public void setLazyInit(boolean lazyInit)
    throws ConfigException
  {
    log.config("lazy-init is deprecated.  Use <startup>lazy</startup> instead.");
    if (lazyInit)
      setStartupMode("lazy");
    else
      setStartupMode("automatic");
  }

  /**
   * Adds a default.
   */
  public void addHostDefault(HostConfig config)
  {
    _hostDefaults.add(config);
  }

  /**
   * Returns the log.
   */
  protected Logger getLog()
  {
    return log;
  }

  /**
   * Returns the current array of application entries.
   */
  public HostController createController(String name)
  {
    Path rootDirectory = getExpandDirectory().lookup(name);

    HostController controller = new HostController(_container, null);

    try {
      controller.setName(name);
      controller.setRootDirectory(rootDirectory);
      controller.setStartupMode(getStartupMode());

      Path jarPath = getArchiveDirectory().lookup(name + ".jar");
      controller.setArchivePath(jarPath);

      controller.addDepend(jarPath);

      String hostName = getHostName();

      if (hostName != null)
	controller.setHostName(EL.evalString(hostName, controller.getVariableResolver()));
      else
	controller.setHostName(name);

      for (int i = 0; i < _hostDefaults.size(); i++)
	controller.addConfigDefault(_hostDefaults.get(i));
    } catch (ConfigException e) {
      controller.setConfigException(e);
      
      log.warning(e.toString());
      log.log(Level.FINER, e.toString(), e);
    } catch (Throwable e) {
      controller.setConfigException(e);
      
      log.log(Level.WARNING, e.toString(), e);
    }

    return controller;
  }

  public boolean equals(Object o)
  {
    if (o == null || ! getClass().equals(o.getClass()))
      return false;

    HostExpandDeployGenerator deploy = (HostExpandDeployGenerator) o;

    Path expandPath = getExpandDirectory();
    Path deployExpandPath = deploy.getExpandDirectory();
    if (expandPath != deployExpandPath &&
	(expandPath == null || ! expandPath.equals(deployExpandPath)))
      return false;

    return true;
  }

  public String toString()
  {
    return "HostExpandDeployGenerator[" + getExpandDirectory() + "]";
  }
}
