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

package com.caucho.sql;

import java.io.*;
import java.sql.*;
import java.util.*;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.lang.reflect.Method;

import javax.sql.*;
import javax.naming.*;
import javax.transaction.*;
import javax.transaction.xa.*;

import javax.resource.spi.ManagedConnectionFactory;

import com.caucho.util.*;
import com.caucho.vfs.*;
import com.caucho.transaction.*;

import com.caucho.loader.EnvironmentLocal;
import com.caucho.loader.Environment;
import com.caucho.loader.EnvironmentListener;
import com.caucho.loader.EnvironmentClassLoader;

import com.caucho.naming.Jndi;

import com.caucho.log.Log;

import com.caucho.config.types.InitProgram;
import com.caucho.config.types.InitParam;
import com.caucho.config.types.Period;
import com.caucho.config.ConfigException;
import com.caucho.config.Config;

import com.caucho.sql.spy.SpyDriver;
import com.caucho.sql.spy.SpyXADataSource;
import com.caucho.sql.spy.SpyConnectionPoolDataSource;

/**
 * Manages a pool of database connections.  In addition, DBPool configures
 * the database connection from a configuration file.
 *
 * <p>Like JDBC 2.0 pooling, DBPool returns a wrapped Connection.  
 * Applications can use that connection just like an unpooled connection.
 * It is more important than ever to <code>close()</code> the connection,
 * because the close returns the connection to the connection pool.
 *
 * <h4>Example using DataSource JNDI style (recommended)</h4>
 *
 * <pre><code>
 * Context env = (Context) new InitialContext().lookup("java:comp/env");
 * DataSource pool = (DataSource) env.lookup("jdbc/test");
 * Connection conn = pool.getConnection();
 * try {
 *   ... // normal connection stuff
 * } finally {
 *   conn.close();
 * }
 * </code></pre>
 *
 * <h4>Configuration</h4>
 *
 * <pre><code>
 * &lt;database name='jdbc/test'>
 *   &lt;init>
 *     &lt;driver>postgresql.Driver&lt;/driver>
 *     &lt;url>jdbc:postgresql://localhost/test&lt;/url>
 *     &lt;user>ferg&lt;/user>
 *     &lt;password>foobar&lt;/password>
 *   &lt;/init>
 * &lt;/database>
 * </code></pre>
 *
 * <h4>Pool limits and timeouts</h4>
 *
 * The pool will only allow getMaxConnections() connections alive at a time.
 * If <code>getMaxConnection</code> connections are already active,
 * <code>getPooledConnection</code> will block waiting for an available
 * connection.  The wait is timed.  If connection-wait-time passes
 * and there is still no connection, <code>getPooledConnection</code>
 * create a new connection anyway.
 *
 * <p>Connections will only stay in the pool for about 5 seconds.  After
 * that they will be removed and closed.  This reduces the load on the DB
 * and also protects against the database dropping old connections.
 */
public class DBPoolImpl implements AlarmListener, EnvironmentListener {
  protected static final Logger log = Log.open(DBPoolImpl.class);
  private static final L10N L = new L10N(DBPoolImpl.class);
  
  /**
   * The beginning of the URL used to connect to a database with
   * this pooled connection driver.
   */
  private static final String URL_PREFIX = "jdbc:caucho:" ;

  /**
   * The key used to look into the properties passed to the
   * connect method to find the username.
   */
  public static final String PROPERTY_USER = "user" ;
  /**
   * The key used to look into the properties passed to the
   * connect method to find the password.
   */
  public static final String PROPERTY_PASSWORD = "password" ;

  // How long an unused connection can remain in the pool
  private static final long MAX_IDLE_TIME = 30000;
  
  private String _name;

  private ArrayList<DriverConfig> _driverList =
    new ArrayList<DriverConfig>();

  private ArrayList<DriverConfig> _backupDriverList =
    new ArrayList<DriverConfig>();

  private ManagedFactoryImpl _mcf;

  private String _user;
  private String _password;

  // total connections allowed in this pool
  private int _maxConnections = 20;
  // time before an idle connection is closed
  private long _maxIdleTime = MAX_IDLE_TIME;
  // max time a connection is allowed to be active (6 hr)
  private long _maxActiveTime = 6L * 3600L * 1000L;
  // max time a connection is allowed in the pool
  private long _maxPoolTime = 24L * 3600L * 1000L;

  // how long to wait for a connection, say 10 minutes
  private long _connectionWaitTime = 600 * 1000;
  private int _connectionWaitCount = (int) (_connectionWaitTime / 1000);

  // connections to create even when the max-connections overflows.
  private int _maxOverflowConnections = 0;

  // true if the pool has started
  private boolean _isStarted;
  // true if the pool has closed
  private boolean _isClosed;
  // if true, the pool can't be closed.
  private boolean _forbidClose;

  // The JDBC table to be used to ping for connection liveness.
  private String _pingTable;
  // The Query used for connection liveness.
  private String _pingQuery;
  // Ping when the connection is reused.
  private boolean _isPing;
  // How long between pings
  private long _pingInterval;

  // True if the pool is transactional
  private boolean _isTransactional = false;
  // The transaction manager if the pool participates in transactions.
  private TransactionManager _tm;
  // how long before the transaction times out
  private long _transactionTimeout = 0;

  private boolean _isSpy;
  private int _spyId;

  private int _maxCloseStatements = 256;
  // The prepared statement cache size.
  private int _preparedStatementCacheSize = 0;

  // The connections currently in the pool.
  // transient ArrayList<PoolItem> _connections = new ArrayList<PoolItem>();

  // Count for debugging ids.
  private int _idCount;

  // The alarm
  private Alarm _alarm;

  /**
   * Null constructor for the Driver interface; called by the JNDI
   * configuration.  Applications should not call this directly.
   */
  public DBPoolImpl()
  {
  }

  /**
   * Returns the Pool's name
   */
  public String getName()
  {
    return _name;
  }

  /**
   * Sets the Pool's name.  Also puts the pool in the classloader's
   * list of pools.
   */
  public void setName(String name)
  {
    _name = name;
  }

  /**
   * Returns the driver config.
   */
  public DriverConfig createDriver()
  {
    DriverConfig driver = new DriverConfig(this);

    _driverList.add(driver);
    
    return driver;
  }

  /**
   * Returns the driver config.
   */
  public DriverConfig createBackupDriver()
  {
    DriverConfig driver = new DriverConfig(this);

    _backupDriverList.add(driver);
    
    return driver;
  }

  /**
   * Sets a driver parameter.
   */
  public void setInitParam(InitParam init)
  {
    DriverConfig driver = _driverList.get(0);
    
    HashMap<String,String> params = init.getParameters();

    Iterator<String> iter = params.keySet().iterator();
    while (iter.hasNext()) {
      String key = iter.next();
      driver.setInitParam(key, params.get(key));
    }
  }

  /**
   * Sets the jdbc-driver config.
   */
  public void setJDBCDriver(Driver jdbcDriver)
    throws SQLException
  {
    DriverConfig driver;
    
    if (_driverList.size() > 0)
      driver = _driverList.get(0);
    else
      driver = createDriver();

    driver.setDriver(jdbcDriver);
  }

  /**
   * Sets the jdbc-driver config.
   */
  public void setPoolDataSource(ConnectionPoolDataSource poolDataSource)
    throws SQLException
  {
    DriverConfig driver;
    
    if (_driverList.size() > 0)
      driver = _driverList.get(0);
    else
      driver = createDriver();

    driver.setPoolDataSource(poolDataSource);
  }

  /**
   * Sets the jdbc-driver config.
   */
  public void setXADataSource(XADataSource xaDataSource)
    throws SQLException
  {
    DriverConfig driver;
    
    if (_driverList.size() > 0)
      driver = _driverList.get(0);
    else
      driver = createDriver();

    driver.setXADataSource(xaDataSource);
  }

  /**
   * Sets the jdbc-driver config.
   */
  public void setURL(String url)
    throws ConfigException
  {
    DriverConfig driver;
    
    if (_driverList.size() > 0)
      driver = _driverList.get(0);
    else
      throw new ConfigException(L.l("The driver must be assigned before the URL."));

    driver.setURL(url);
  }
  
  /**
   * Returns the connection's user.
   */
  public String getUser()
  {
    return _user;
  }
  
  /**
   * Sets the connection's user.
   */
  public void setUser(String user)
  {
    _user = user;
  }
  
  /**
   * Returns the connection's password
   */
  public String getPassword()
  {
    return _password;
  }
  
  /**
   * Sets the connection's password
   */
  public void setPassword(String password)
  {
    _password = password;
  }
  
  /**
   * Get the maximum number of pooled connections.
   */
  public int getMaxConnections()
  {
    return _maxConnections;
  }
  
  /**
   * Sets the maximum number of pooled connections.
   */
  public void setMaxConnections(int maxConnections)
  {
    _maxConnections = maxConnections;
  }
  
  /**
   * Get the total number of connections
   */
  public int getTotalConnections()
  {
    // return _connections.size();
    return 0;
  }
  
  /**
   * Sets the time to wait for a connection when all are used.
   */
  public void setConnectionWaitTime(Period waitTime)
  {
    long period = waitTime.getPeriod();
    
    _connectionWaitTime = period;
    
    if (period < 0)
      _connectionWaitCount = 3600; // wait for an hour == infinity
    else {
      _connectionWaitCount = (int) ((period + 999) / 1000);
      
      if (_connectionWaitCount <= 0)
        _connectionWaitCount = 1;
    }
  }
  
  /**
   * Gets the time to wait for a connection when all are used.
   */
  public long getConnectionWaitTime()
  {
    return _connectionWaitTime;
  }
  
  /**
   * The number of connections to overflow if the connection pool fills
   * and there's a timeout.
   */
  public void setMaxOverflowConnections(int maxOverflowConnections)
  {
    _maxOverflowConnections = maxOverflowConnections;
  }
  
  /**
   * The number of connections to overflow if the connection pool fills
   * and there's a timeout.
   */
  public int getMaxOverflowConnections()
  {
    return _maxOverflowConnections;
  }

  /**
   * Sets the transaction timeout.
   */
  public void setTransactionTimeout(Period period)
  {
    _transactionTimeout = period.getPeriod();
  }

  /**
   * Gets the transaction timeout.
   */
  public long getTransactionTimeout()
  {
    return _transactionTimeout;
  }

  /**
   * Sets the max statement.
   */
  public void setMaxCloseStatements(int max)
  {
    _maxCloseStatements = max;
  }

  /**
   * Gets the max statement.
   */
  public int getMaxCloseStatements()
  {
    return _maxCloseStatements;
  }

  /**
   * Returns the prepared statement cache size.
   */
  public int getPreparedStatementCacheSize()
  {
    return _preparedStatementCacheSize;
  }

  /**
   * Sets the prepared statement cache size.
   */
  public void setPreparedStatementCacheSize(int size)
  {
    _preparedStatementCacheSize = size;
  }
  
  /**
   * Get the time in milliseconds a connection will remain in the pool before
   * being closed.
   */
  public long getMaxIdleTime()
  {
    if (_maxIdleTime > Long.MAX_VALUE / 2)
      return -1;
    else
      return _maxIdleTime;
  }
  
  /**
   * Set the time in milliseconds a connection will remain in the pool before
   * being closed.
   */
  public void setMaxIdleTime(Period idleTime)
  {
    long period = idleTime.getPeriod();
    
    if (period < 0)
      _maxIdleTime = Long.MAX_VALUE / 2;
    else if (period < 1000L)
      _maxIdleTime = 1000L;
    else
      _maxIdleTime = period;
  }
  
  /**
   * Get the time in milliseconds a connection will remain in the pool before
   * being closed.
   */
  public long getMaxPoolTime()
  {
    if (_maxPoolTime > Long.MAX_VALUE / 2)
      return -1;
    else
      return _maxPoolTime;
  }
  
  /**
   * Set the time in milliseconds a connection will remain in the pool before
   * being closed.
   */
  public void setMaxPoolTime(Period maxPoolTime)
  {
    long period = maxPoolTime.getPeriod();
    
    if (period < 0)
      _maxPoolTime = Long.MAX_VALUE / 2;
    else if (period == 0)
      _maxPoolTime = 1000L;
    else
      _maxPoolTime = period;
  }
  
  /**
   * Get the time in milliseconds a connection can remain active.
   */
  public long getMaxActiveTime()
  {
    if (_maxActiveTime > Long.MAX_VALUE / 2)
      return -1;
    else
      return _maxActiveTime;
  }
  
  /**
   * Set the time in milliseconds a connection can remain active.
   */
  public void setMaxActiveTime(Period maxActiveTime)
  {
    long period = maxActiveTime.getPeriod();
    
    if (period < 0)
      _maxActiveTime = Long.MAX_VALUE / 2;
    else if (period == 0)
      _maxActiveTime = 1000L;
    else
      _maxActiveTime = period;
  }

  /**
   * Get the table to 'ping' to see if the connection is still live.
   */
  public String getPingTable()
  {
    return _pingTable;
  }

  /**
   * Set the table to 'ping' to see if the connection is still live.
   *
   * @param pingTable name of the SQL table to ping.
   */
  public void setPingTable(String pingTable)
  {
    _pingTable = pingTable;
    
    if (pingTable != null)
      _pingQuery = "select 1 from " + pingTable + " where 1=0";
    else
      _pingQuery = null;
  }

  /**
   * Returns the ping query.
   */
  public String getPingQuery()
  {
    return _pingQuery;
  }

  /**
   * If true, the pool will ping when attempting to reuse a connection.
   */
  public boolean getPingOnReuse()
  {
    return _isPing;
  }

  /**
   * Set the table to 'ping' to see if the connection is still live.
   */
  public void setPingOnReuse(boolean pingOnReuse)
  {
    _isPing = pingOnReuse;
  }

  /**
   * If true, the pool will ping in the idle pool.
   */
  public boolean getPingOnIdle()
  {
    return _isPing;
  }

  /**
   * Set the table to 'ping' to see if the connection is still live.
   */
  public void setPingOnIdle(boolean pingOnIdle)
  {
    _isPing = pingOnIdle;
  }

  /**
   * Set true if pinging is enabled.
   */
  public void setPing(boolean ping)
  {
    _isPing = ping;
  }

  /**
   * Returns true if pinging is enabled.
   */
  public boolean isPing()
  {
    return _isPing;
  }
  
  /**
   * Sets the time to ping for ping-on-idle
   */
  public void setPingInterval(Period interval)
  {
    _pingInterval = interval.getPeriod();
    
    if (_pingInterval < 0)
      _pingInterval = Long.MAX_VALUE / 2;
    else if (_pingInterval < 1000)
      _pingInterval = 1000;
  }
  
  /**
   * Gets how often the ping for ping-on-idle
   */
  public long getPingInterval()
 {
    return _pingInterval;
  }

  /**
   * Set the transaction manager for this pool.
   */
  public void setTransactionManager(TransactionManager tm)
  {
    _tm = tm;
  }

  /**
   * Returns the transaction manager.
   */
  public TransactionManager getTransactionManager()
  {
    return _tm;
  }

  /**
   * Returns true if this is transactional.
   */
  public boolean isXA()
  {
    return _isTransactional;
  }

  /**
   * Returns true if this is transactional.
   */
  public void setXA(boolean isTransactional)
  {
    _isTransactional = isTransactional;
  }

  /**
   * Set the output for spying.
   */
  public void setSpy(boolean isSpy)
  {
    _isSpy = isSpy;
  }

  /**
   * Return true for a spy.
   */
  public boolean isSpy()
  {
    return _isSpy;
  }

  /**
   * Returns the next spy id.
   */
  public int newSpyId()
  {
    return _spyId++;
  }

  /**
   * Returns true if the pool supports transactions.
   */
  public boolean isTransactional()
  {
    return _isTransactional;
  }

  int createPoolId()
  {
    return _idCount++;
  }

  /**
   * Sets the timeout for a database login.
   */
  public void setLoginTimeout(int seconds) throws SQLException
  {
  }
  
  /**
   * Gets the timeout for a database login.
   */
  public int getLoginTimeout() throws SQLException
  {
    return 0;
  }
  /**
   * Sets the debugging log for the connection.
   */
  public void setLogWriter(PrintWriter out) throws SQLException
  {
  }
  
  /**
   * Sets the debugging log for the connection.
   */
  public PrintWriter getLogWriter() throws SQLException
  {
    return null;
  }

  /**
   * Initialize the pool.
   */
  public void init()
    throws Exception
  {
    Environment.addEnvironmentListener(this);
    
    // _alarm = new Alarm("db-pool", this, 60000);

    /*
    if (_pingInterval > 0 &&
        _pingInterval < _maxIdleTime &&
        _pingInterval < 60000L)
      _alarm.queue(_pingInterval);
    else if (_maxIdleTime > 60000)
      _alarm.queue(60000);
    else
      _alarm.queue(_maxIdleTime);
    */

    try {
      if (_tm == null) {
        Object obj = new InitialContext().lookup("java:comp/TransactionManager");

        if (obj instanceof TransactionManager)
          _tm = (TransactionManager) obj;
      }
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
    }

    if (_isTransactional && _tm == null)
      throw new ConfigException(L.l("Can't find TransactionManager in java:comp/TransactionManager for transaction-enabled DBPool."));

    for (int i = 0; i < _driverList.size(); i++) {
      DriverConfig driver = _driverList.get(i);

      if (driver.getUser() == null)
	driver.setUser(_user);
      if (driver.getPassword() == null)
	driver.setPassword(_password);
      
      driver.initDriver();
      driver.initDataSource(_isTransactional, _isSpy);

      if (driver.getXADataSource() == null)
	_isTransactional = false;
    }

    DriverConfig []drivers = new DriverConfig[_driverList.size()];
    _driverList.toArray(drivers);

    for (int i = 0; i < _backupDriverList.size(); i++) {
      DriverConfig driver = _backupDriverList.get(i);

      if (driver.getUser() == null)
	driver.setUser(_user);
      if (driver.getPassword() == null)
	driver.setPassword(_password);
      
      driver.initDriver();
      driver.initDataSource(_isTransactional, _isSpy);

      if (driver.getXADataSource() == null)
	_isTransactional = false;
    }

    DriverConfig []backupDrivers = new DriverConfig[_backupDriverList.size()];
    _backupDriverList.toArray(backupDrivers);

    _mcf = new ManagedFactoryImpl(this, drivers, backupDrivers);
    
    if (_name != null) {
      String name = _name;
      if (! name.startsWith("java:"))
        name = "java:comp/env/" + name;

      if (drivers[0].getURL() != null)
        log.config("database " + name + " starting (URL:" + drivers[0].getURL() + ")");
      else
        log.config("database " + name + " starting");

      // XXX: actually should be proxy
      // Jndi.bindDeep(name, this);
    }
  }

  /**
   * Returns the managed connection factory.
   */
  ManagedConnectionFactory getManagedConnectionFactory()
  {
    return _mcf;
  }

  /**
   * Returns a new or pooled connection.
   */
  /*
  public Connection getConnection() throws SQLException
  {
    return getConnection(null, null);
  }
  */
  
  /**
   * Return a connection.  The connection will only be pooled if
   * user and password match the configuration.  In general, applications
   * should use the null-argument getConnection().
   *
   * @param user database user
   * @param password database password
   * @return a database connection
   */
  /*
  public Connection getConnection(String user, String password)
    throws SQLException
  {
    boolean isPooled = false;
    PoolItem poolItem = null;
    int count = 25;

    while (poolItem == null && count-- >= 0) {
      if (user != null && ! user.equals(_driver.getUser()))
	poolItem = createConnection(user, password);
      else if (password != null && ! password.equals(_driver.getPassword()))
	poolItem = createConnection(user, password);
      else
	poolItem = getPooledConnection();

      // If the connection is transactional, enlist it.
      try {
	if (_isTransactional && poolItem.getXid() == null) {
	  Transaction trans = _tm.getTransaction();

	  if (trans != null)
	    trans.enlistResource(poolItem);
	}
      } catch (Throwable e) {
	log.log(Level.WARNING, e.toString(), e);

	try {
	  poolItem.connectionErrorOccurred(null);
	  poolItem.connectionClosed(null);
	} catch (Throwable e1) {
	  log.log(Level.FINE, e1.toString(), e1);
	}

	poolItem = null;
      }
    }

    try {
      return poolItem.getConnection();
    } catch (Throwable e) {
      poolItem.close();
      
      throw SQLExceptionWrapper.create(e);
    }
  }
  */

  /**
   * Returns a pooled connection.  It will be returned to the pool when
   * the close method is called.
   *
   * <p>If <code>getMaxConnection</code> connections are already active,
   * <code>getPooledConnection</code> will block waiting for an available
   * connection.  The wait is timed.  If connection-wait-time passes
   * and there is still no connection, <code>getPooledConnection</code>
   * create a new connection anyway.
   *
   * @return a pooled database connection.
   */
  /*
  private PoolItem getPooledConnection() throws SQLException
  {
    PoolItem poolItem = null;
    ArrayList<PoolItem> connections = _connections;

    if (_connections == null)
      throw new SQLException(L.l("can't get connection from closed pool `{0}'", _name));

    boolean createConnection = false;

    synchronized (connections) {
      for (int i = 0; ! _isClosed && i < _connectionWaitCount; i++) {
        // If we're in a transaction, try to reuse one of the
        // connections in the transaction that's closed but
        // not committed.
        if (_isTransactional) {
          try {
            TransactionImpl trans = (TransactionImpl) _tm.getTransaction();
            Xid xid = trans == null ? null : trans.getXid();

            if (xid != null) {
              for (int j = connections.size() - 1; j >= 0; j--) {
                poolItem = (PoolItem) connections.get(j);

                if (poolItem.isDead()) {
                  connections.remove(j);
                  continue;
                }

                if (! poolItem.allocateXA(xid))
                  continue;

                if (isValid(poolItem)) {
                  if (log.isLoggable(Level.FINER))
                    log.finer("reusing connection " + poolItem);
                  
                  return poolItem;
                }
                else if (poolItem.isDead())
                  connections.remove(j);
              }
            }
          } catch (Exception e) {
            try {
              if (poolItem != null)
                poolItem.close();
            } catch (Throwable e1) {
            }
            
            log.log(Level.FINE, e.toString(), e);
          }
        }
        
        for (int j = connections.size() - 1; j >= 0; j--) {
          poolItem = (PoolItem) connections.get(j);

          if (poolItem.isDead()) {
            connections.remove(j);
            continue;
          }
          
          if (! poolItem.allocate())
            continue;

          if (isValid(poolItem))
            return poolItem;
          else if (poolItem.isDead())
            connections.remove(j);
        }

        // If we can create a connection, break to do so
        if (connections.size() < _maxConnections)
          break;
        
        // If no connections in pool and can't create, then sleep
        try {
          if (log.isLoggable(Level.FINE))
            log.fine("wait for connection (" +
                     getActiveConnections() + ", " +
                     getTotalConnections() + ")");
            
          // wait for a freed connection
          connections.wait(1000);
        } catch (InterruptedException e) {
          log.log(Level.FINER, e.toString(), e);
        }
      }

      if (connections.size() < _maxConnections + _maxOverflowConnections) {
        if (_maxConnections <= connections.size()) {
          log.info("creating an overflow connection [active:" +
                   getActiveConnections() + ", total:" +
                   getTotalConnections() +
                   "] url=" + _driver.getURL() + " user=" + _driver.getUser());
        }

        createConnection = true;
      }
    }

    if (createConnection)
      return createConnection(_driver.getUser(), _driver.getPassword());
    
    log.warning("can't connect with full pool [active:" +
                getActiveConnections() + ", total:" + getTotalConnections() +
                "] url=" + _driver.getURL() + " user=" + _driver.getUser());

    throw new SQLException(L.l("Can't open connection with full database pool ({0})", String.valueOf(getTotalConnections())));
  }
  */

  /**
   * Returns true if the pool item is still okay to use.
   */
  /*
  private boolean isValid(PoolItem poolItem)
  {
    try {
      if (! poolItem.isValid())
        return false;
      
      if (! _pingOnReuse || ping(poolItem.getConnection())) {
        // If the connection is still valid, use it

        return true;
      }
    } catch (Throwable e) {
      log.log(Level.FINE, e.toString(), e);
    }
          
    if (log.isLoggable(Level.FINE))
      log.fine("connection died in pool " +
               poolItem.getId() + ":" + getName() + " [total:" +
               getTotalConnections() + "]");
            
    // If the connection died in the pool, kill it
    try {
      poolItem.close();
    } catch (SQLException e) {
      log.log(Level.FINE, e.toString(), e);
    }

    return false;
  }
  */

  /**
   * Create a new connection
   *
   * @param user the user's database name for the user
   * @param password the user's database password
   */
  /*
  private PoolItem createConnection(String user, String password)
    throws SQLException
  {
    PooledConnection conn = null;

    if (_isClosed)
      throw new SQLException(L.l("can't create connection from closed pool"));

    if (! _isStarted)
      initDataSource();

    conn = _driver.createConnection(user, password);

    return createPoolItem(conn);
  }
  */

  /**
   * Initialize the pool's data source
   *
   * <ul>
   * <li>If data-source is set, look it up in JNDI.
   * <li>Else if the driver is a pooled or xa data source, use it.
   * <li>Else create wrappers.
   * </ul>
   */
  synchronized void initDataSource()
    throws SQLException
  {
    if (_isStarted)
      return;

    _isStarted = true;

    for (int i = 0; i < _driverList.size(); i++) {
      DriverConfig driver = _driverList.get(i);
      
      driver.initDataSource(_isTransactional, _isSpy);
    }

    try {
      if (_isTransactional && _tm == null) {
        Object obj = new InitialContext().lookup("java:comp/TransactionManager");

        if (obj instanceof TransactionManager)
          _tm = (TransactionManager) obj;
      }
    } catch (NamingException e) {
      throw new SQLExceptionWrapper(e);
    }
  }

  /**
   * Creates a new database pool item.
   *
   * @param conn the underlying pooled connection.
   */
  /*
  private PoolItem createPoolItem(PooledConnection conn)
  {
    PoolItem poolItem = new PoolItem(this, conn);

    if (! poolItem.allocate())
      throw new IllegalStateException();
    
    synchronized (_connections) {
      _connections.add(poolItem);
    }

    if (log.isLoggable(Level.FINE)) {
      log.fine("create " + poolItem.getId() + ":" + getName() +
               " [active:" + getActiveConnections() +
               ", total:" + getTotalConnections() + "]");
    }

    return poolItem;
  }
  */

  /**
   * Removes an item from the pool.
   */
  /*
  void removeItem(PoolItem item)
  {
    synchronized (_connections) {
      _connections.remove(item);
      
      if (_connections.size() + 1 >= _maxConnections)
        _connections.notifyAll();
    }
    
    if (log.isLoggable(Level.FINE)) {
      log.fine("close-on-error " + item.getId() + ":" + getName() +
               " [active:" + getActiveConnections() +
               ", total:" + getTotalConnections() + "]");
    }
  }
  */

  /**
   * At the alarm, close all connections which have been sitting in
   * the pool for too long.
   *
   * @param alarm the alarm event.
   */
  public void handleAlarm(Alarm alarm)
  {
    if (_isClosed)
      return;
  }

  /**
   * Callback when the environment starts.
   */
  public void environmentStart(EnvironmentClassLoader loader)
  {
  }

  /**
   * Callback when the class loader dies.
   */
  public void environmentStop(EnvironmentClassLoader loader)
  {
    forceClose();
  }

  /**
   * Returns true if the pool is closed.
   */
  public boolean isClosed()
  {
    return _isClosed;
  }
  
  /**
   * Close the pool, closing the connections.
   */
  public void close()
  {
    if (_forbidClose)
      throw new IllegalStateException("illegal to call close() for this DBPool");
    forceClose();
  }

  /**
   * Close all the connections in the pool.
   */
  public void forceClose()
  {
    if (_isClosed)
      return;

    _isClosed = true;

    if (log.isLoggable(Level.FINE))
      log.fine("closing pool " + getName());
  }

  /**
   * Returns a string description of the pool.
   */
  public String toString()
  {
    return "DBPoolImpl[" + _name + "]";
  }
}

