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

package com.caucho.amber.hibernate;

import java.io.*;

import org.xml.sax.SAXException;

import com.caucho.util.L10N;

import com.caucho.vfs.Path;
import com.caucho.vfs.Depend;

import com.caucho.config.ConfigException;
import com.caucho.config.NodeBuilder;

import com.caucho.amber.AmberManager;

public class HibernateParser {
  private static final L10N L = new L10N(HibernateParser.class);

  public static void parse(AmberManager amberManager, Path path)
    throws ConfigException, IOException
  {
    try {
      HibernateMapping mapping = new HibernateMapping(amberManager);

      mapping.setDependency(new Depend(path));

      NodeBuilder builder = new NodeBuilder();
      builder.setCompactSchema("com/caucho/amber/hibernate/hibernate-mapping.rnc");

      builder.configure(mapping, path);
    } catch (SAXException e) {
      if (e.getCause() instanceof ConfigException)
	throw (ConfigException) e.getCause();
      else
	throw new ConfigException(e);
    }
  }
}
