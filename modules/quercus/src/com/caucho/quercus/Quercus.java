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

package com.caucho.quercus;

import com.caucho.config.ConfigException;
import com.caucho.quercus.annotation.ClassImplementation;
import com.caucho.quercus.env.*;
import com.caucho.quercus.function.AbstractFunction;
import com.caucho.quercus.lib.db.JavaSqlDriverWrapper;
import com.caucho.quercus.lib.file.FileModule;
import com.caucho.quercus.lib.session.QuercusSessionManager;
import com.caucho.quercus.module.*;
import com.caucho.quercus.page.InterpretedPage;
import com.caucho.quercus.page.PageManager;
import com.caucho.quercus.page.QuercusPage;
import com.caucho.quercus.parser.QuercusParser;
import com.caucho.quercus.program.*;
import com.caucho.util.*;
import com.caucho.vfs.*;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.sql.Connection;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facade for the PHP language.
 */
public class Quercus
{
  private static L10N L = new L10N(Quercus.class);
  private static final Logger log = Log.open(Quercus.class);

  private static HashSet<String> _superGlobals
    = new HashSet<String>();

  private static IniDefinitions _ini = new IniDefinitions();

  private final PageManager _pageManager;
  private final QuercusSessionManager _sessionManager;

  private final ClassLoader _loader;
  
  private ModuleContext _moduleContext;

  private HashMap<String, StringValue> _internMap
    = new HashMap<String, StringValue>();

  private HashMap<String, ModuleInfo> _modules
    = new HashMap<String, ModuleInfo>();

  private HashSet<ModuleStartupListener> _moduleStartupListeners
    = new HashSet<ModuleStartupListener>();

  private HashSet<String> _extensionSet
    = new HashSet<String>();

  private HashMap<String, Value> _constMap
    = new HashMap<String, Value>();

  private HashMap<String, AbstractFunction> _funMap
    = new HashMap<String, AbstractFunction>();

  private HashMap<String, AbstractFunction> _lowerFunMap
    = new HashMap<String, AbstractFunction>();

  /*
  private ClassDef _stdClassDef;
  private QuercusClass _stdClass;
  */

  private HashMap<String, ClassDef> _staticClasses
    = new HashMap<String, ClassDef>();

  private HashMap<String, ClassDef> _lowerStaticClasses
    = new HashMap<String, ClassDef>();

  private HashMap<String, JavaClassDef> _javaClassWrappers
    = new HashMap<String, JavaClassDef>();
  
  private LruCache<String, String> _classNotFoundCache
    = new LruCache<String, String>(64);

  private HashMap<String, JavaClassDef> _lowerJavaClassWrappers
    = new HashMap<String, JavaClassDef>();

  private final IniDefinitions _iniDefinitions = new IniDefinitions();
  
  private IdentityHashMap<String, Value> _iniMap;

  private HashMap<Value, Value> _serverEnvMap
    = new HashMap<Value, Value>();

  private IntMap _classNameMap = new IntMap(8192);
  private ClassDef []_classMap = new ClassDef[256];
  
  private IntMap _constantNameMap = new IntMap(8192);

  private IntMap _functionNameMap = new IntMap(8192);
  private AbstractFunction []_functionMap = new AbstractFunction[256];

  private LruCache<String, QuercusProgram> _evalCache
    = new LruCache<String, QuercusProgram>(4096);

  private int _includeCacheMax = 8192;
  private long _includeCacheTimeout = 10000L;

  private TimedCache<IncludeKey, Path> _includeCache;

  //private LruCache<DefinitionKey,SoftReference<DefinitionState>> _defCache
  //    = new LruCache<DefinitionKey,SoftReference<DefinitionState>>(4096);

  private long _defCacheHitCount;
  private long _defCacheMissCount;

  // XXX: needs to be a timed LRU
  //private LruCache<String, SessionArrayValue> _sessionMap
  //  = new LruCache<String, SessionArrayValue>(4096);

  private HashMap<String, Object> _specialMap
    = new HashMap<String, Object>();

  private String _scriptEncoding;

  private String _phpVersion = "5.2.0";
  private String _mySqlVersion;

  private boolean _isStrict;
  private boolean _isRequireSource;
  
  private boolean _isConnectionPool = true;

  private DataSource _database;

  private HashMap<String,DataSource> _databaseMap
    = new HashMap<String,DataSource>();

  private long _staticId;

  private Path _pwd;
  private Path _workDir;
  
  private ServletContext _servletContext;
  
  /**
   * Constructor.
   */
  public Quercus()
  {
    _loader = Thread.currentThread().getContextClassLoader();
    
    _moduleContext = getLocalContext();
    
    _pageManager = createPageManager();
    
    _sessionManager = createSessionManager();
  }
  
  /**
   * Returns the working directory.
   */
  public Path getPwd()
  {
    if (_pwd == null)
      _pwd = new FilePath(System.getProperty("user.dir"));
    
    return _pwd;
  }

  /**
   * Sets the working directory.
   */
  public void setPwd(Path path)
  {
    _pwd = path;
  }

  public Path getWorkDir()
  {
    if (_workDir == null)
      _workDir = getPwd().lookup("WEB-INF/work");

    return _workDir;
  }

  public void setWorkDir(Path workDir)
  {
    _workDir = workDir;
  }

  public String getCookieName()
  {
    return "JSESSIONID";
  }

  public long getDependencyCheckInterval()
  {
    return 2000L;
  }

  public int getIncludeCacheMax()
  {
    return _includeCacheMax;
  }

  public void setIncludeCacheMax(int cacheMax)
  {
    _includeCacheMax = cacheMax;
  }

  public void setIncludeCacheTimeout(long timeout)
  {
    _includeCacheTimeout = timeout;
  }

  public long getIncludeCacheTimeout()
  {
    return _includeCacheTimeout;
  }

  public String getVersion()
  {
    return "Open Source 3.2.0";
  }

  public String getVersionDate()
  {
    return "20070628T2777";
  }

  protected PageManager createPageManager()
  {
    return new PageManager(this);
  }

  protected QuercusSessionManager createSessionManager()
  {
    return new QuercusSessionManager();
  }
  
  /**
   * Returns the context for this class loader.
   */
  public final ModuleContext getLocalContext()
  {
    return getLocalContext(_loader);
  }

  public ModuleContext getLocalContext(ClassLoader loader)
  {
    synchronized (this) {
      if (_moduleContext == null) {
	_moduleContext = createModuleContext(null, loader);
	_moduleContext.init();
      }
    }

    return _moduleContext;
  }

  protected ModuleContext createModuleContext(ModuleContext parent,
					      ClassLoader loader)
  {
    return new ModuleContext(parent, loader);
  }

  /**
   * Returns the module context.
   */
  public ModuleContext getModuleContext()
  {
    return _moduleContext;
  }

  public QuercusSessionManager getQuercusSessionManager()
  {
    return _sessionManager;
  }

  /**
   * true if the pages should be compiled.
   */
  public boolean isCompile()
  {
    return _pageManager.isCompile();
  }
  
  /*
   * Returns true if this is the Professional version.
   */
  public boolean isPro()
  {
    return false;
  }
  
  /*
   * Returns true if Quercus is running under Resin.
   */
  public boolean isResin()
  {
    return false;
  }
  
  /**
   * Returns true if unicode.semantics is on.
   */
  public boolean isUnicodeSemantics()
  {
    boolean value = getIniBoolean("unicode.semantics");

    return value;
  }
  
  /*
   * Returns true if URLs may be arguments of include().
   */
  public boolean isAllowUrlInclude()
  {
    return getIniBoolean("allow_url_include");
  }
  
  /*
   * Returns true if URLs may be arguments of fopen().
   */
  public boolean isAllowUrlFopen()
  {
    return getIniBoolean("allow_url_fopen");
  }

  public StringValue createString(String v)
  {
    if (isUnicodeSemantics())
      return new UnicodeBuilderValue(v);
    else
      return new StringBuilderValue(v);
  }
  
  /**
   * Set true if pages should be compiled.
   */
  public void setCompile(boolean isCompile)
  {
    _pageManager.setCompile(isCompile);
  }

  /**
   * Set true if pages should be compiled.
   */
  public void setLazyCompile(boolean isCompile)
  {
    _pageManager.setLazyCompile(isCompile);
  }
  
  /*
   * true if interpreted pages should be used if pages fail to compile.
   */
  public void setCompileFailover(boolean isCompileFailover)
  {
    _pageManager.setCompileFailover(isCompileFailover);
  }

  /*
   * Returns the expected encoding of php scripts.
   */
  public String getScriptEncoding()
  {
    if (_scriptEncoding != null)
      return _scriptEncoding;
    else if (isUnicodeSemantics())
      return "utf-8";
    else
      return "iso-8859-1";
  }

  /*
   * Sets the expected encoding of php scripts.
   */
  public void setScriptEncoding(String encoding)
  {
    _scriptEncoding = encoding;
  }

  /*
   * Returns the mysql version to report to to PHP applications.
   * It is user set-able to allow cloaking of the underlying mysql
   * JDBC driver version for application compatibility.
   */
  public String getMysqlVersion()
  {
    return _mySqlVersion;
  }

  /*
   * Sets the mysql version to report to applications.  This cloaks
   * the underlying JDBC driver version, so that when an application
   * asks for the mysql version, this version string is returned instead.
   */
  public void setMysqlVersion(String version)
  {
    _mySqlVersion = version;
  }

  public String getPhpVersion()
  {
    return _phpVersion;
  }

  public void setPhpVersion(String version)
  {
    _phpVersion = version;
  }
  
  /*
   * Sets the ServletContext.
   */
  public void setServletContext(ServletContext servletContext)
  {
    _servletContext = servletContext;
  }
  
  /*
   * Returns the ServletContext.
   */
  public ServletContext getServletContext()
  {
    return _servletContext;
  }

  /**
   * Sets the default data source.
   */
  public void setDatabase(DataSource database)
  {
    _database = database;
  }

  /**
   * Gets the default data source.
   */
  public DataSource getDatabase()
  {
    return _database;
  }

  /**
   * Gets the default data source.
   */
  public DataSource findDatabase(String driver, String url)
  {
    if (_database != null)
      return _database;
    else {
      try {
	synchronized (_databaseMap) {
	  String key = driver + ";" + url;
	
	  DataSource database = _databaseMap.get(key);

	  if (database != null)
	    return database;
	
	  ClassLoader loader = Thread.currentThread().getContextClassLoader();
      
	  Class cls = loader.loadClass(driver);
        
	  Object ds = cls.newInstance();
        
	  if (ds instanceof DataSource)
	    database = (DataSource) ds;
	  else
	    database = new JavaSqlDriverWrapper((java.sql.Driver) ds, url);

	  _databaseMap.put(key, database);

	  return database;
	}
      } catch (ClassNotFoundException e) {
        throw new QuercusModuleException(e);
      } catch (InstantiationException e) {
        throw new QuercusModuleException(e);
      } catch (IllegalAccessException e) {
        throw new QuercusModuleException(e);
      }
    }
  }
  
  /*
   * Marks the connection for removal from the connection pool.
   */
  public void markForPoolRemoval(Connection conn)
  {
  }

  /**
   * Unwrap connection if necessary.
   */
  public Connection getConnection(Connection conn)
  {
    return conn;
  }
  
  /**
   * Unwrap statement if necessary.
   */
  public java.sql.Statement getStatement(java.sql.Statement stmt)
  {
    return stmt;
  }

  /**
   * Sets the strict mode.
   */
  public void setStrict(boolean isStrict)
  {
    _isStrict = isStrict;
  }

  /**
   * Gets the strict mode.
   */
  public boolean isStrict()
  {
    return _isStrict;
  }
  
  /*
   * Gets the max size of the page cache.
   */
  public int getPageCacheEntries()
  {
    return _pageManager.getPageCacheEntries();
  }
  
  /*
   * Sets the capacity of the page cache.
   */
  public void setPageCacheEntries(int entries)
  {
    _pageManager.setPageCacheEntries(entries);
  }
  
  /*
   * Set to true if compiled pages need to be backed by php source files.
   */
  public void setRequireSource(boolean isRequireSource)
  {
    _isRequireSource = isRequireSource;
  }
  
  /*
   * Returns whether the php source is required for compiled files.
   */
  public boolean isRequireSource()
  {
    return _isRequireSource;
  }
  
  /*
   * Turns connection pooling on or off.
   */
  public void setConnectionPool(boolean isEnable)
  {
    _isConnectionPool = isEnable;
  }
  
  /*
   * Returns true if connections should be pooled.
   */
  public boolean isConnectionPool()
  {
    return _isConnectionPool;
  }

  /**
   * Adds a module
   */
  /*
  public void addModule(QuercusModule module)
    throws ConfigException
  {
    try {
      introspectPhpModuleClass(module.getClass());
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }
  */

  /**
   * Adds a java class
   */
  public void addJavaClass(String name, Class type)
    throws ConfigException
  {
    try {
      if (type.isAnnotationPresent(ClassImplementation.class))
        _moduleContext.introspectJavaImplClass(name, type, null);
      else
        _moduleContext.introspectJavaClass(name, type, null, null);
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }

  /**
   * Adds a java class
   */
  public void addJavaClass(String phpName, String className)
  {

    Class type;

    try {
      type = Class.forName(className, false, _loader);
    }
    catch (ClassNotFoundException e) {
      throw new QuercusRuntimeException(L.l("`{0}' not valid: {1}", className, e.toString()), e);
    }
    
    addJavaClass(phpName, type);
  }

  /**
   * Adds a impl class
   */
  public void addImplClass(String name, Class type)
    throws ConfigException
  {
    throw new UnsupportedOperationException("XXX: need to merge with ModuleContext: " + name);
    /*
    try {
      introspectJavaImplClass(name, type, null);
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
    */
  }

  /**
   * Adds a java class
   */
  public JavaClassDef getJavaClassDefinition(Class type, String className)
  {
    JavaClassDef def;
    
    if (_classNotFoundCache.get(className) != null)
      return null;
    
    synchronized (_javaClassWrappers) {
      def = _javaClassWrappers.get(className);
    }

    if (def == null) {
      try {
	def = getModuleContext().getJavaClassDefinition(type, className);

	synchronized (_javaClassWrappers) {
	  _javaClassWrappers.put(className, def);
	}
      } catch (RuntimeException e) {
	throw e;
      } catch (Exception e) {
	throw new QuercusRuntimeException(e);
      }
    }

    def.init();

    return def;
  }
  
  /**
   * Adds a java class
   */
  public JavaClassDef getJavaClassDefinition(String className)
  {
    JavaClassDef def;
    
    if (_classNotFoundCache.get(className) != null)
      return null;
    
    synchronized (_javaClassWrappers) {
      def = _javaClassWrappers.get(className);

      if (def == null) {
        try {
          def = getModuleContext().getJavaClassDefinition(className);

          _javaClassWrappers.put(className, def);
        } catch (RuntimeException e) {
          _classNotFoundCache.put(className, className);
          
          throw e;
        } catch (Exception e) {
          throw new QuercusRuntimeException(e);
        }
      }
    }

    def.init();

    return def;
  }
  
  /**
   * Finds the java class wrapper.
   */
  public ClassDef findJavaClassWrapper(String name)
  {
    synchronized (_javaClassWrappers) {
      ClassDef def = _javaClassWrappers.get(name);

      if (def != null)
	return def;

      return _lowerJavaClassWrappers.get(name.toLowerCase());
    }
  }

  /**
   * Sets an ini file.
   */
  public void setIniFile(Path path)
  {
    // XXX: Not sure why this dependency would be useful
    // Environment.addDependency(new Depend(path));

    if (path.canRead()) {
      Env env = new Env(this);

      Value result = FileModule.parse_ini_file(env, path, false);

      if (result instanceof ArrayValue) {
        ArrayValue array = (ArrayValue) result;

        for (Map.Entry<Value,Value> entry : array.entrySet()) {
          setIni(entry.getKey().toString(), entry.getValue().toString());
        }
      }
    }
  }

  /**
   * Returns the IniDefinitions for all ini that have been defined by modules.
   */
  public IniDefinitions getIniDefinitions()
  {
    return _iniDefinitions;
  }

  /**
   * Returns a map of the ini values that have been explicitly set.
   */
  public IdentityHashMap<String, Value> getIniMap(boolean create)
  {
    if (_iniMap == null && create)
      _iniMap = new IdentityHashMap<String, Value>();

    return _iniMap;
  }

  /**
   * Sets an ini value.
   */
  public void setIni(String name, StringValue value)
  {
    _iniDefinitions.get(name).set(this, value);
  }

  /**
   * Sets an ini value.
   */
  public void setIni(String name, String value)
  {
    _iniDefinitions.get(name).set(this, value);
  }

  /**
   * Returns an ini value.
   */
  public boolean getIniBoolean(String name)
  {
    return _iniDefinitions.get(name).getAsBoolean(this);
  }
  
  /**
   * Returns an ini value as a long.
   */
  public long getIniLong(String name)
  {
    return _iniDefinitions.get(name).getAsLongValue(this).toLong();
  }
  
  /**
   * Sets a server env value.
   */
  public void setServerEnv(String name, String value)
  {
    setServerEnv(createString(name), createString(value));
  }

  /**
   * Sets a server env value.
   */
  public void setServerEnv(StringValue name, StringValue value)
  {
    _serverEnvMap.put(name, value);
  }

  /**
   * Gets a server env value.
   */
  public Value getServerEnv(StringValue name)
  {
    return _serverEnvMap.get(name);
  }

  /**
   * Returns the server env map.
   */
  public HashMap<Value,Value> getServerEnvMap()
  {
    return _serverEnvMap;
  }

  /**
   * Returns the relative path.
   */
  /*
  public String getClassName(Path path)
  {
    return _pageManager.getClassName(path);
  }
  */

  /**
   * Returns an include path.
   */
  public Path getIncludeCache(StringValue include,
                              String includePath,
                              Path pwd,
                              Path scriptPwd)
  {
    IncludeKey key = new IncludeKey(include, includePath, pwd, scriptPwd);

    Path path = _includeCache.get(key);

    return path;
  }

  /**
   * Adds an include path.
   */
  public void putIncludeCache(StringValue include,
                              String includePath,
                              Path pwd,
                              Path scriptPwd,
                              Path path)
  {
    IncludeKey key = new IncludeKey(include, includePath, pwd, scriptPwd);

    _includeCache.put(key, path);
  }

  /**
   * Returns the definition cache hit count.
   */
  public long getDefCacheHitCount()
  {
    return _defCacheHitCount;
  }

  /**
   * Returns the definition cache miss count.
   */
  public long getDefCacheMissCount()
  {
    return _defCacheMissCount;
  }

  /**
   * Returns the definition state for an include.
   */
  /*
  public DefinitionState getDefinitionCache(DefinitionKey key)
  {
    SoftReference<DefinitionState> defStateRef = _defCache.get(key);

    if (defStateRef != null) {
      DefinitionState defState = defStateRef.get();

      if (defState != null) {
        _defCacheHitCount++;

        return defState.copyLazy();
      }
    }

    _defCacheMissCount++;

    return null;
  }
  */

  /**
   * Returns the definition state for an include.
   */
  /*
  public void putDefinitionCache(DefinitionKey key,
                                 DefinitionState defState)
  {
    _defCache.put(key, new SoftReference<DefinitionState>(defState.copy()));
  }
  */

  /**
   * Clears the definition cache.
   */
  public void clearDefinitionCache()
  {
    // _defCache.clear();
  }

  /**
   * Returns true if a precompiled page exists
   */
  public boolean includeExists(Path path)
  {
    return _pageManager.precompileExists(path);
  }

  /**
   * Parses a quercus program.
   *
   * @param path the source file path
   * @return the parsed program
   * @throws IOException
   */
  public QuercusPage parse(Path path)
    throws IOException
  {
    return _pageManager.parse(path);
  }

  /**
   * Parses a quercus program.
   *
   * @param path the source file path
   * @return the parsed program
   * @throws IOException
   */
  public QuercusPage parse(Path path, String fileName, int line)
    throws IOException
  {
    return _pageManager.parse(path, fileName, line);
  }

  /**
   * Parses a quercus program.
   *
   * @param path the source file path
   * @return the parsed program
   * @throws IOException
   */
  public QuercusPage parse(ReadStream is)
    throws IOException
  {
    return new InterpretedPage(QuercusParser.parse(this, is));
  }

  /**
   * Parses a quercus string.
   *
   * @param code the source code
   * @return the parsed program
   * @throws IOException
   */
  public QuercusProgram parseCode(String code)
    throws IOException
  {
    QuercusProgram program = _evalCache.get(code);

    if (program == null) {
      program = QuercusParser.parseEval(this, code);
      _evalCache.put(code, program);
    }

    return program;
  }

  /**
   * Parses a quercus string.
   *
   * @param code the source code
   * @return the parsed program
   * @throws IOException
   */
  public QuercusProgram parseEvalExpr(String code)
    throws IOException
  {
    // XXX: possible conflict with parse eval because of the
    // return value changes
    QuercusProgram program = _evalCache.get(code);

    if (program == null) {
      program = QuercusParser.parseEvalExpr(this, code);
      _evalCache.put(code, program);
    }

    return program;
  }

  /**
   * Parses a function.
   *
   * @param args the arguments
   * @param code the source code
   * @return the parsed program
   * @throws IOException
   */
  public AbstractFunction parseFunction(String name, String args, String code)
    throws IOException
  {
    return QuercusParser.parseFunction(this, name, args, code);
  }

  /**
   * Returns the function with the given name.
   */
  public AbstractFunction findFunction(String name)
  {
    AbstractFunction fun = _funMap.get(name);

    if ((fun == null) && ! isStrict())
      fun = _lowerFunMap.get(name.toLowerCase());

    return fun;
  }

  /**
   * Returns the function with the given name.
   */
  public AbstractFunction findFunctionImpl(String name)
  {
    AbstractFunction fun = _funMap.get(name);

    return fun;
  }

  /**
   * Returns the function with the given name.
   */
  public AbstractFunction findLowerFunctionImpl(String lowerName)
  {
    AbstractFunction fun = _lowerFunMap.get(lowerName);

    return fun;
  }

  /**
   * Returns an array of the defined functions.
   */
  public ArrayValue getDefinedFunctions()
  {
    ArrayValue internal = new ArrayValueImpl();

    for (String name : _funMap.keySet()) {
      internal.put(name);
    }

    return internal;
  }

  //
  // name to id mappings
  //

  /**
   * Returns the id for a function name.
   */
  public int getFunctionId(String name)
  {
    if (! isStrict())
      name = name.toLowerCase();

    synchronized (_functionNameMap) {
      int id = _functionNameMap.get(name);

      if (id < 0) {
        id = _functionNameMap.size();

        _functionNameMap.put(name, id);

        if (_functionMap.length <= id) {
          AbstractFunction []functionMap = new AbstractFunction[id + 256];
          System.arraycopy(_functionMap, 0,
			   functionMap, 0, _functionMap.length);
          _functionMap = functionMap;
        }

        _functionMap[id] = new UndefinedFunction(name);
      }

      return id;
    }
  }

  /**
   * Returns the id for a function name.
   */
  public int findFunctionId(String name)
  {
    if (! isStrict())
      name = name.toLowerCase();

    // IntMap is internally synchronized
    return _functionNameMap.get(name);
  }

  /**
   * Returns the number of functions
   */
  public int getFunctionIdCount()
  {
    return _functionNameMap.size();
  }

  /**
   * Returns the undefined functions
   */
  public AbstractFunction []getFunctionMap()
  {
    return _functionMap;
  }

  public int setFunction(String name, AbstractFunction fun)
  {
    int id = findFunctionId(name);
    
    synchronized (_functionNameMap) {
      _functionMap[id] = fun;
    }

    return id;
  }

  /**
   * Returns the id for a class name.
   */
  public int getClassId(String name)
  {
    name = name.toLowerCase();

    synchronized (_classNameMap) {
      int id = _classNameMap.get(name);

      if (id < 0) {
	id = _classNameMap.size();

	_classNameMap.put(name, id);

	if (_classMap.length <= id) {
	  ClassDef []classMap = new ClassDef[id + 256];
	  System.arraycopy(_classMap, 0, classMap, 0, _classMap.length);
	  _classMap = classMap;
	}

	// _classMap[id] = new UndefinedClass(name);
      }

      return id;
    }
  }

  /**
   * Returns the id for a function name.
   */
  public int findClassId(String name)
  {
    synchronized (_classNameMap) {
      return _classNameMap.get(name);
    }
  }

  /**
   * Returns the number of classes
   */
  public int getClassIdCount()
  {
    return _classNameMap.size();
  }

  /**
   * Returns the undefined functions
   */
  public ClassDef []getClassDefMap()
  {
    return _classMap;
  }

  /**
   * Returns the id for a constant
   */
  public int getConstantId(String name)
  {
    name = name.toLowerCase();

    synchronized (_constantNameMap) {
      int id = _constantNameMap.get(name);

      if (id < 0) {
	id = _constantNameMap.size();

	_constantNameMap.put(name, id);
      }

      return id;
    }
  }

  /**
   * Returns the number of defined constants
   */
  public int getConstantIdSize()
  {
    return _constantNameMap.size();
  }

  /**
   * Returns true if the variable is a superglobal.
   */
  public static boolean isSuperGlobal(String name)
  {
    return _superGlobals.contains(name);
  }

  /**
   * Returns the stdClass definition.
   */
  public QuercusClass getStdClass()
  {
    return _moduleContext.getStdClass();
  }

  /**
   * Returns the class with the given name.
   */
  public ClassDef findClass(String name)
  {
    synchronized (_staticClasses) {
      ClassDef def = _staticClasses.get(name);

      if (def == null)
	def = _lowerStaticClasses.get(name.toLowerCase());

      return def;
    }
  }

  /**
   * Returns the class maps.
   */
  public HashMap<String, ClassDef> getClassMap()
  {
    return _staticClasses;
  }

  /**
   * Returns the module with the given name.
   */
  public QuercusModule findModule(String name)
  {
    ModuleInfo moduleInfo =  _modules.get(name);
    QuercusModule module = null;

    if (moduleInfo != null)
      module = moduleInfo.getModule();
    else
      module = getModuleContext().findModule(name);

    if (module == null)
      throw new IllegalStateException(L.l("'{0}' is an unknown quercus module",
					  name));

    return module;
  }

  /**
   * Returns a list of the modules that have some startup code to run.
   */
  public HashSet<ModuleStartupListener> getModuleStartupListeners()
  {
    return _moduleStartupListeners;
  }

  /**
   * Returns true if an extension is loaded.
   */
  public boolean isExtensionLoaded(String name)
  {
    return _extensionSet.contains(name);
  }

  /**
   * Returns true if an extension is loaded.
   */
  public HashSet<String> getLoadedExtensions()
  {
    return _extensionSet;
  }

  /**
   * Returns true if an extension is loaded.
   */
  public Value getExtensionFuncs(String name)
  {
    ArrayValue value = null;

    for (ModuleInfo moduleInfo : _modules.values()) {
      Set<String> extensionSet = moduleInfo.getLoadedExtensions();

      if (extensionSet.contains(name)) {
        for (String functionName : moduleInfo.getFunctions().keySet()) {
          if (value == null)
            value = new ArrayValueImpl();

          value.put(functionName);
        }
      }
    }

    if (value != null)
      return value;
    else
      return BooleanValue.FALSE;
  }
  
  public Collection<ModuleInfo> getModules()
  {
    return _modules.values();
  }

  public HashMap<String, Value> getConstMap()
  {
    return _constMap;
  }

  /**
   * Initialize the enging
   */
  public void init()
  {
    initModules();
    initClasses();

    _workDir = getWorkDir();

    _iniDefinitions.addAll(_ini);
    
    _includeCache = new TimedCache<IncludeKey, Path>(getIncludeCacheMax(),
						     getIncludeCacheTimeout());

    initLocal();
  }

  public void addModule(QuercusModule module)
  {
    ModuleInfo info = new ModuleInfo(_moduleContext,
				     module.getClass().getName(),
				     module);

    addModuleInfo(info);
  }

  /**
   * Initializes from the ModuleContext
   */
  private void initModules()
  {
    for (ModuleInfo info : _moduleContext.getModules()) {
      addModuleInfo(info);
    }
  }

  protected void addModuleInfo(ModuleInfo info)
  {
    _modules.put(info.getName(), info);
    
    if (info.getModule() instanceof ModuleStartupListener)
      _moduleStartupListeners.add((ModuleStartupListener)info.getModule());

    for (String ext : info.getLoadedExtensions())
      _extensionSet.add(ext);

    Map<String, Value> map = info.getConstMap();

    if (map != null)
      _constMap.putAll(map);

    _iniDefinitions.addAll(info.getIniDefinitions());

    for (Map.Entry<String, AbstractFunction> entry
	   : info.getFunctions().entrySet()) {
      String funName = entry.getKey();
      AbstractFunction fun = entry.getValue();
      
      _funMap.put(funName, fun);
      _lowerFunMap.put(funName.toLowerCase(), fun);

      int id = getFunctionId(funName);
      _functionMap[id] = fun;
    }
  }

  /**
   * Initializes from the ModuleContext
   */
  private void initClasses()
  {
    for (Map.Entry<String,JavaClassDef> entry
	   : _moduleContext.getWrapperMap().entrySet()) {
      String name = entry.getKey();
      JavaClassDef def = entry.getValue();
      
      _javaClassWrappers.put(name, def);
      _lowerJavaClassWrappers.put(name.toLowerCase(), def);
    }
    
    for (Map.Entry<String,ClassDef> entry
	   : _moduleContext.getClassMap().entrySet()) {
      
      String name = entry.getKey();
      ClassDef def = entry.getValue();
      
      _staticClasses.put(name, def);
      _lowerStaticClasses.put(name.toLowerCase(), def);
    }
  }

  /**
   * Interns a string.
   */
  public StringValue intern(String name)
  {
    synchronized (_internMap) {
      StringValue value = _internMap.get(name);

      if (value == null) {
        name = name.intern();

        value = new StringBuilderValue(name);
        _internMap.put(name, value);
      }

      return value;
    }
  }

  /**
   * Returns a named constant.
   */
  public Value getConstant(String name)
  {
    return _constMap.get(name);
  }

  public String createStaticName()
  {
    return ("s" + _staticId++).intern();
  }

  /**
   * Loads the session from the backing.
   */
  public SessionArrayValue loadSession(Env env, String sessionId)
  {
    long now = Alarm.getCurrentTime();

    SessionArrayValue session =
      _sessionManager.getSession(env, sessionId, now);

    if (session == null)
      session = _sessionManager.createSession(env, sessionId, now);

    return session;
  }

  /**
   * Saves the session to the backing.
   */
  public void saveSession(Env env, SessionArrayValue session)
  {
    _sessionManager.saveSession(env, session);
  }

  /**
   * Removes the session from the backing.
   */
  public void destroySession(String sessionId)
  {
    _sessionManager.removeSession(sessionId);
  }

  /**
   * Loads a special value
   */
  public Object getSpecial(String key)
  {
    synchronized (_specialMap) {
      return _specialMap.get(key);
    }
  }

  /**
   * Saves a special value
   */
  public void setSpecial(String key, Object value)
  {
    synchronized (_specialMap) {
      _specialMap.put(key, value);
    }
  }

  public static Value objectToValue(Object obj)
  {
    if (obj == null)
      return NullValue.NULL;
    else if (Byte.class.equals(obj.getClass())
	     || Short.class.equals(obj.getClass())
	     || Integer.class.equals(obj.getClass())
	     || Long.class.equals(obj.getClass())) {
      return LongValue.create(((Number) obj).longValue());
    } else if (Float.class.equals(obj.getClass()) ||
               Double.class.equals(obj.getClass())) {
      return DoubleValue.create(((Number) obj).doubleValue());
    } else if (String.class.equals(obj.getClass())) {
      // XXX: i18n
      return new StringBuilderValue((String) obj);
    } else {
      // XXX: unknown types, e.g. Character?

      return null;
    }
  }

  /**
   * Initialize local configuration, e.g. finding the PHP and PEAR libraries
   */
  protected void initLocal()
  {
    StringBuilder sb = new StringBuilder(".");

    Path pwd = getPwd();

    String []paths = new String[] {
      "/usr/share/php", "/usr/lib/php", "/usr/local/lib/php",
      "/usr/share/pear", "/usr/lib/pear", "/usr/local/lib/pear"
    };

    for (String path : paths) {
      if (pwd.lookup(path).isDirectory()) {
	sb.append(":").append(pwd.lookup(path).getPath());
      }
    }

    setIni("include_path", sb.toString());
  }

  public void start()
  {
  }

  public Env createEnv(QuercusPage page,
                       WriteStream out,
                       HttpServletRequest request,
                       HttpServletResponse response)
  {
    return new Env(this, page, out, request, response);
  }

  public void close()
  {
    _sessionManager.close();
    _pageManager.close();
  }

  public static Value exnConstructor(Env env, Value obj, String msg)
  {
    if (obj != null) {
      obj.putField(env, "message", new UnicodeValueImpl(msg));
    }

    return NullValue.NULL;

  }

  static class IncludeKey {
    private final StringValue _include;
    private final String _includePath;
    private final Path _pwd;
    private final Path _scriptPwd;

    IncludeKey(StringValue include,
	       String includePath,
	       Path pwd,
	       Path scriptPwd)
    {
      _include = include;
      _includePath = includePath;
      _pwd = pwd;
      _scriptPwd = scriptPwd;
    }

    public int hashCode()
    {
      int hash = 37;

      hash = 65537 * hash + _include.hashCode();
      hash = 65537 * hash + _includePath.hashCode();
      hash = 65537 * hash + _pwd.hashCode();
      hash = 65537 * hash + _scriptPwd.hashCode();

      return hash;
    }

    public boolean equals(Object o)
    {
      if (! (o instanceof IncludeKey))
        return false;

      IncludeKey key = (IncludeKey) o;

      return (_include.equals(key._include)
	      && _includePath.equals(key._includePath)
	      && _pwd.equals(key._pwd)
	      && _scriptPwd.equals(key._scriptPwd));
    }
  }

  static {
    _superGlobals.add("GLOBALS");
    _superGlobals.add("_COOKIE");
    _superGlobals.add("_ENV");
    _superGlobals.add("_FILES");
    _superGlobals.add("_GET");
    _superGlobals.add("_POST");
    _superGlobals.add("_SERVER");
    _superGlobals.add("_SESSION");
    _superGlobals.add("_REQUEST");
    
    /*
    String includePath;
    
    if (Path.isWindows())
      includePath = "."
                    + FileModule.PATH_SEPARATOR
                    + "C:\\php5\\pear";
    else
      includePath = "."
                    + FileModule.PATH_SEPARATOR
                    + "/usr/share/php"
                    + FileModule.PATH_SEPARATOR
                    + "/usr/share/pear";
    
    INI_INCLUDE_PATH = _ini.add("include_path", includePath, IniDefinition.PHP_INI_ALL);
    */
  }

  public static final IniDefinition INI_INCLUDE_PATH
    = _ini.add("include_path", ".", IniDefinition.PHP_INI_ALL);
  public static final IniDefinition INI_REGISTER_LONG_ARRAYS
    = _ini.add("register_long_arrays", true, IniDefinition.PHP_INI_PERDIR);
  public static final IniDefinition INI_ALWAYS_POPULATE_RAW_POST_DATA
    = _ini.add("always_populate_raw_post_data", false, IniDefinition.PHP_INI_PERDIR);
  
  // unicode ini
  public static final IniDefinition INI_UNICODE_SEMANTICS
    = _ini.add("unicode.semantics", false, IniDefinition.PHP_INI_SYSTEM);
  public static final IniDefinition INI_UNICODE_FALLBACK_ENCODING
    = _ini.add("unicode.fallback_encoding", "utf-8", IniDefinition.PHP_INI_ALL);
  public static final IniDefinition INI_UNICODE_FROM_ERROR_MODE
    = _ini.add("unicode.from_error_mode", "2", IniDefinition.PHP_INI_ALL);
  public static final IniDefinition INI_UNICODE_FROM_ERROR_SUBST_CHAR
    = _ini.add("unicode.from_error_subst_char", "3f", IniDefinition.PHP_INI_ALL);
  public static final IniDefinition INI_UNICODE_HTTP_INPUT_ENCODING
    = _ini.add("unicode.http_input_encoding", null, IniDefinition.PHP_INI_ALL);
  public static final IniDefinition INI_UNICODE_OUTPUT_ENCODING
    = _ini.add("unicode.output_encoding", null, IniDefinition.PHP_INI_ALL);
  public static final IniDefinition INI_UNICODE_RUNTIME_ENCODING
    = _ini.add("unicode.runtime_encoding", null, IniDefinition.PHP_INI_ALL);
  public static final IniDefinition INI_UNICODE_SCRIPT_ENCODING
    = _ini.add("unicode.script_encoding", null, IniDefinition.PHP_INI_ALL);

}

