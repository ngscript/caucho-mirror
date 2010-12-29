/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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
 * @author Sam
 */


package com.caucho.tools.profiler;

import java.sql.*;
import java.util.Map;
import java.util.Properties;

public final class ConnectionWrapper
  implements Connection
{
  private final Connection _connection;
  private final ProfilerPoint _profilerPoint;

  public ConnectionWrapper(ProfilerPoint profilerPoint, Connection connection)
  {
    _profilerPoint = profilerPoint;
    _connection = connection;
  }

  private ProfilerPoint createProfilerPoint(String sql)
  {
    // XXX: need to categorize under _profilerPoint even if it is
    // not the parent in the call stack at time of execution
    // switch _profilerPoint to DatabaseProfilerPoint, move createPP to DatabasePP
    return _profilerPoint.addProfilerPoint(sql);
  }

  private StatementWrapper wrap(Statement statement)
  {
    return new StatementWrapper(_profilerPoint, statement);
  }

  private PreparedStatementWrapper wrap(String sql, PreparedStatement statement)
  {
    ProfilerPoint profilerPoint = createProfilerPoint(sql);

    return new PreparedStatementWrapper(profilerPoint, statement);
  }

  private CallableStatementWrapper wrap(String sql, CallableStatement statement)
  {
    ProfilerPoint profilerPoint = createProfilerPoint(sql);

    return new CallableStatementWrapper(profilerPoint, statement);
  }

  public Statement createStatement()
    throws SQLException
  {
    return wrap(_connection.createStatement());
  }

  public Statement createStatement(int resultSetType, int resultSetConcurrency,
                                   int resultSetHoldability)
    throws SQLException
  {
    return wrap(_connection.createStatement(resultSetType,
                                            resultSetConcurrency,
                                            resultSetHoldability));
  }

  public Statement createStatement(int resultSetType, int resultSetConcurrency)
    throws SQLException
  {
    return wrap(_connection.createStatement(resultSetType,
                                            resultSetConcurrency));
  }

  public PreparedStatement prepareStatement(String sql)
    throws SQLException
  {
    ProfilerPoint profilerPoint = _profilerPoint.addProfilerPoint(sql);

    return wrap(sql, _connection.prepareStatement(sql));
  }

  public PreparedStatement prepareStatement(String sql,
                                            int resultSetType,
                                            int resultSetConcurrency,
                                            int resultSetHoldability)
    throws SQLException
  {
    ProfilerPoint profilerPoint = _profilerPoint.addProfilerPoint(sql);

    return wrap(sql, _connection.prepareStatement(sql,
                                                  resultSetType,
                                                  resultSetConcurrency,
                                                  resultSetHoldability));
  }

  public CallableStatement prepareCall(String sql)
    throws SQLException
  {
    return wrap(sql, _connection.prepareCall(sql));
  }

  public CallableStatement prepareCall(String sql,
                                       int resultSetType,
                                       int resultSetConcurrency,
                                       int resultSetHoldability)
    throws SQLException
  {
    return wrap(sql, _connection.prepareCall(sql,
                                        resultSetType,
                                        resultSetConcurrency,
                                        resultSetHoldability));
  }

  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
    throws SQLException
  {
    return wrap(sql, _connection.prepareStatement(sql,
                                             autoGeneratedKeys));
  }

  public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
    throws SQLException
  {
    return wrap(sql, _connection.prepareStatement(sql,
                                             columnIndexes));
  }

  public PreparedStatement prepareStatement(String sql, String[] columnNames)
    throws SQLException
  {
    return wrap(sql, _connection.prepareStatement(sql, columnNames));
  }

  public PreparedStatement prepareStatement(String sql,
                                            int resultSetType,
                                            int resultSetConcurrency)
    throws SQLException
  {
    return wrap(sql, _connection.prepareStatement(sql,
                                                  resultSetType,
                                                  resultSetConcurrency));
  }

  public CallableStatement prepareCall(String sql,
                                       int resultSetType,
                                       int resultSetConcurrency)
    throws SQLException
  {
    return wrap(sql, _connection.prepareCall(sql,
                                             resultSetType,
                                             resultSetConcurrency));
  }

  public String nativeSQL(String sql)
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.nativeSQL(sql);
    }
    finally {
      profiler.finish();
    }
  }

  public void setAutoCommit(boolean autoCommit)
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.setAutoCommit(autoCommit);
    }
    finally {
      profiler.finish();
    }
  }

  public boolean getAutoCommit()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.getAutoCommit();
    }
    finally {
      profiler.finish();
    }
  }

  public void commit()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.commit();
    }
    finally {
      profiler.finish();
    }
  }

  public void rollback()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.rollback();
    }
    finally {
      profiler.finish();
    }
  }

  public boolean isClosed()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.isClosed();
    }
    finally {
      profiler.finish();
    }
  }

  public DatabaseMetaData getMetaData()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.getMetaData();
    }
    finally {
      profiler.finish();
    }
  }

  public void setReadOnly(boolean readOnly)
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.setReadOnly(readOnly);
    }
    finally {
      profiler.finish();
    }
  }

  public boolean isReadOnly()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.isReadOnly();
    }
    finally {
      profiler.finish();
    }
  }

  public void setCatalog(String catalog)
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.setCatalog(catalog);
    }
    finally {
      profiler.finish();
    }
  }

  public String getCatalog()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.getCatalog();
    }
    finally {
      profiler.finish();
    }
  }

  public void setTransactionIsolation(int level)
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.setTransactionIsolation(level);
    }
    finally {
      profiler.finish();
    }
  }

  public int getTransactionIsolation()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.getTransactionIsolation();
    }
    finally {
      profiler.finish();
    }
  }

  public SQLWarning getWarnings()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.getWarnings();
    }
    finally {
      profiler.finish();
    }
  }

  public void clearWarnings()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.clearWarnings();
    }
    finally {
      profiler.finish();
    }
  }

  public Map<String, Class<?>> getTypeMap()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.getTypeMap();
    }
    finally {
      profiler.finish();
    }
  }

  public void setTypeMap(Map<String, Class<?>> map)
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.setTypeMap(map);
    }
    finally {
      profiler.finish();
    }
  }

  public void setHoldability(int holdability)
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.setHoldability(holdability);
    }
    finally {
      profiler.finish();
    }
  }

  public int getHoldability()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.getHoldability();
    }
    finally {
      profiler.finish();
    }
  }

  public Savepoint setSavepoint()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.setSavepoint();
    }
    finally {
      profiler.finish();
    }
  }

  public Savepoint setSavepoint(String name)
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      return _connection.setSavepoint(name);
    }
    finally {
      profiler.finish();
    }
  }

  public void rollback(Savepoint savepoint)
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.rollback(savepoint);
    }
    finally {
      profiler.finish();
    }
  }

  public void releaseSavepoint(Savepoint savepoint)
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.releaseSavepoint(savepoint);
    }
    finally {
      profiler.finish();
    }
  }

  public void close()
    throws SQLException
  {
    Profiler profiler = _profilerPoint.start();

    try {
      _connection.close();
    }
    finally {
      profiler.finish();
    }
  }

  public String toString()
  {
    return "ConnectionWrapper[" + _profilerPoint.getName() + "]";
  }

    public Clob createClob() throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Blob createBlob() throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public NClob createNClob() throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public SQLXML createSQLXML() throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean isValid(int timeout) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String getClientInfo(String name) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Properties getClientInfo() throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}