/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
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

package com.caucho.loader.enhancer;

import com.caucho.bytecode.ByteCodeClassMatcher;
import com.caucho.bytecode.ByteCodeClassScanner;
import com.caucho.bytecode.ByteCodeParser;
import com.caucho.bytecode.JClass;
import com.caucho.bytecode.JavaClass;
import com.caucho.bytecode.JavaClassLoader;
import com.caucho.java.WorkDir;
import com.caucho.java.gen.GenClass;
import com.caucho.java.gen.JavaClassGenerator;
import com.caucho.loader.DynamicClassLoader;
import com.caucho.loader.EnvironmentLocal;
import com.caucho.util.CharBuffer;
import com.caucho.util.L10N;
import com.caucho.util.Log;
import com.caucho.vfs.Path;
import com.caucho.vfs.ReadStream;

import java.io.ByteArrayInputStream;
import java.lang.instrument.*;
import java.util.ArrayList;
import java.security.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the enhancement
 */
public class EnhancerManager
  implements ClassFileTransformer, ByteCodeClassMatcher
{
  private static final L10N L = new L10N(EnhancerManager.class);
  private static final Logger log = Log.open(EnhancerManager.class);

  private static EnvironmentLocal<EnhancerManager> _localEnhancer =
    new EnvironmentLocal<EnhancerManager>();

  private EnhancerManager _parent;
  private DynamicClassLoader _loader;

  private Path _workPath;

  private JavaClassLoader _jClassLoader = new JavaClassLoader();
  private JavaClassGenerator _javaGen = new JavaClassGenerator();
  
  private ArrayList<ClassEnhancer> _classEnhancerList =
    new ArrayList<ClassEnhancer>();
  
  private EnhancerManager(ClassLoader loader)
  {
    for (;
	 loader != null && ! (loader instanceof DynamicClassLoader);
	 loader = loader.getParent()) {
    }

    _loader = (DynamicClassLoader) loader;

    if (loader != null)
      _parent = getLocalEnhancer(loader.getParent());

    if (_parent != null) {
      _classEnhancerList.addAll(_parent._classEnhancerList);
    }
  }

  public static EnhancerManager create()
  {
    return create(Thread.currentThread().getContextClassLoader());
  }

  public static EnhancerManager create(ClassLoader loader)
  {
    EnhancerManager enhancer = _localEnhancer.getLevel(loader);

    if (enhancer == null) {
      enhancer = new EnhancerManager(loader);
      _localEnhancer.set(enhancer, loader);

      for (; loader != null; loader = loader.getParent()) {
	if (loader instanceof DynamicClassLoader) {
	  ((DynamicClassLoader) loader).addTransformer(enhancer);
	  break;
	}
      }
    }

    return enhancer;
  }

  public static EnhancerManager getLocalEnhancer(ClassLoader loader)
  {
    return _localEnhancer.get(loader);
  }

  /**
   * Returns the JClassLoader.
   */
  public JavaClassLoader getJavaClassLoader()
  {
    return _jClassLoader;
  }

  /**
   * Gets the work path.
   */
  public Path getWorkPath()
  {
    if (_workPath != null)
      return _workPath;
    else
      return WorkDir.getLocalWorkDir(_loader);
  }

  /**
   * Sets the work path.
   */
  public void setWorkPath(Path workPath)
  {
    _workPath = workPath;
  }

  /**
   * Gets the work path.
   */
  public final Path getPreWorkPath()
  {
    return getWorkPath().lookup("pre-enhance");
  }

  /**
   * Gets the work path.
   */
  public final Path getPostWorkPath()
  {
    return getWorkPath().lookup("post-enhance");
  }

  /**
   * Adds a class enhancer.
   */
  public void addClassEnhancer(ClassEnhancer classEnhancer)
  {
    _classEnhancerList.add(classEnhancer);
  }

  /**
   * Adds a class annotation.
   */
  public void addClass(ClassEnhancerConfig config)
  {
    config.setEnhancerManager(this);

    _classEnhancerList.add(config);
  }
  
  /**
   * Returns the enhanced .class or null if no enhancement.
   */
  public byte[] transform(ClassLoader loader,
			  String className,
			  Class oldClass,
			  ProtectionDomain domain,
			  byte []buffer)
  {
    int length = buffer.length;
    
    ByteCodeClassScanner scanner;

    // XXX: temp
    scanner = new ByteCodeClassScanner(className, buffer, 0, length, this);

    if (scanner.scan()) {
      try {
	ByteCodeParser parser = new ByteCodeParser();
	parser.setClassLoader(_jClassLoader);
	
	ByteArrayInputStream is;
	is = new ByteArrayInputStream(buffer, 0, length);
      
	JavaClass jClass = parser.parse(is);

	return enhance(jClass);
      } catch (Throwable e) {
	throw new EnhancerRuntimeException(e);
      }
    }

    return null;
  }

  /**
   * Enhances the given class.
   */
  public byte []enhance(JClass jClass)
    throws ClassNotFoundException
  {
    String className = jClass.getName().replace('/', '.');
    String extClassName = className + "__ResinExt";

    try {
      EnhancerPrepare prepare = new EnhancerPrepare();
      prepare.setWorkPath(getWorkPath());
      prepare.setClassLoader(_loader);

      for (ClassEnhancer enhancer : _classEnhancerList) {
	if (enhancer.shouldEnhance(className)) {
	  prepare.addEnhancer(enhancer);
	}
      }

      //prepare.renameClass(className, extClassName);
      prepare.renameClass(className, className);
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);

      throw new ClassNotFoundException(e.toString());
    }

    boolean hasEnhancer = false;
    GenClass genClass = new GenClass(extClassName);
    genClass.setSuperClassName(className);

    for (ClassEnhancer enhancer : _classEnhancerList) {
      if (enhancer.shouldEnhance(className)) {
	try {
	  hasEnhancer = true;
	  enhancer.enhance(genClass, jClass, extClassName);
	} catch (RuntimeException e) {
	  throw e;
	} catch (Exception e) {
	  throw new RuntimeException(e);
	}
      }
    }
    // XXX: class-wide enhancements need to go first

    try {
      if (hasEnhancer) {
	_javaGen.setWorkDir(getPreWorkPath());
	_javaGen.generate(genClass);
	_javaGen.compilePendingJava();
      }

      EnhancerFixup fixup = new EnhancerFixup();
      fixup.setJavaClassLoader(_jClassLoader);
      fixup.setClassLoader(_loader);
      fixup.setWorkPath(getWorkPath());

      for (ClassEnhancer enhancer : _classEnhancerList) {
	if (enhancer.shouldEnhance(className)) {
	  fixup.addEnhancer(enhancer);
	}
      }
      
      fixup.fixup(className, extClassName);

      Path path = getPostWorkPath().lookup(className.replace('.', '/') + ".class");
      byte []buffer = new byte[(int) path.getLength()];
      
      ReadStream is = path.openRead();
      try {
	is.readAll(buffer, 0, buffer.length);
      } finally {
	is.close();
      }

      return buffer;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
      
      throw new ClassNotFoundException(e.getMessage());
    }

    // return null;
  }

  /**
   * Returns true for a matching class.
   */
  public boolean isClassMatch(String className)
  {
    if (className.lastIndexOf('$') >= 0) {
      int p = className.lastIndexOf('$');
      char ch = className.charAt(p + 1);

      if (ch >= '0' && ch <= '9')
	return false;
    }
    else if (className.indexOf('+') > 0 ||
	     className.indexOf('-') > 0)
      return false;
    
    for (int i = 0; i < _classEnhancerList.size(); i++) {
      if (_classEnhancerList.get(i).shouldEnhance(className)) {
	return true;
      }
    }

    return false;
  }

  /**
   * Returns true for a matching class.
   */
  public boolean isMatch(CharBuffer cb)
  {
    return false;
  }
}
