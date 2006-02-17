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

package com.caucho.db.sql;

import java.io.IOException;

import java.util.HashMap;
import java.util.Iterator;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.sql.SQLException;

import com.caucho.util.FreeList;

import com.caucho.log.Log;

import com.caucho.db.store.Transaction;

import com.caucho.db.table.TableIterator;

import com.caucho.db.jdbc.GeneratedKeysResultSet;

/**
 * Represents the state of the query at any particular time.
 */
public class QueryContext {
  private static final Logger log = Log.open(QueryContext.class);

  private static final FreeList<QueryContext> _freeList =
    new FreeList<QueryContext>(64);

  private Transaction _xa;
  private TableIterator []_tableIterators;

  private Data []_parameters = new Data[8];

  private GroupItem _tempGroupItem;
  private GroupItem _groupItem;

  private boolean _isReturnGeneratedKeys;
  private SelectResult _result;
  private GeneratedKeysResultSet _generatedKeys;
  private int _rowUpdateCount;

  private HashMap<GroupItem,GroupItem> _groupMap;

  private byte []_buffer = new byte[256];

  private QueryContext()
  {
    _tempGroupItem = GroupItem.allocate(new boolean[8]);
  }

  /**
   * Returns a new query context.
   */
  public static QueryContext allocate()
  {
    QueryContext queryContext = _freeList.allocate();
    
    if (queryContext == null)
      queryContext = new QueryContext();

    queryContext.clearParameters();

    return queryContext;
  }

  public void clearParameters()
  {
    for (int i = _parameters.length - 1; i >= 0; i--) {
      if (_parameters[i] == null)
	_parameters[i] = new Data();
      
      _parameters[i].clear();
    }
  }

  /**
   * Initializes the query state.
   */
  public void init(Transaction xa, TableIterator []tableIterators)
  {
    _xa = xa;
    _tableIterators = tableIterators;

    _rowUpdateCount = 0;
    _groupItem = _tempGroupItem;
    _groupItem.init(0, null);
  }

  /**
   * Initializes the group.
   */
  public void initGroup(int size, boolean []isGroupByFields)
  {
    _groupItem = _tempGroupItem;
    
    _groupItem.init(size, isGroupByFields);

    if (_groupMap == null)
      _groupMap = new HashMap<GroupItem,GroupItem>();
  }

  /**
   * Selects the actual group item.
   */
  public void selectGroup()
  {
    GroupItem item = _groupMap.get(_groupItem);

    if (item == null) {
      item = _groupItem.allocateCopy();

      _groupMap.put(item, item);
    }

    _groupItem = item;
  }

  /**
   * Returns the group results.
   */
  Iterator<GroupItem> groupResults()
  {
    if (_groupMap == null)
      return com.caucho.util.NullIterator.create();
    
    Iterator<GroupItem> results = _groupMap.values().iterator();
    _groupMap = null;

    return results;
  }
  
  /**
   * Sets the current result.
   */
  void setGroupItem(GroupItem item)
  {
    _groupItem = item;
  }


  /**
   * Returns the table iterator.
   */
  public TableIterator []getTableIterators()
  {
    return _tableIterators;
  }

  /**
   * Sets the transaction.
   */
  public void setTransaction(Transaction xa)
  {
    _xa = xa;
  }

  /**
   * Returns the transaction.
   */
  public Transaction getTransaction()
  {
    return _xa;
  }

  /**
   * Returns the temp buffer.
   */
  public byte []getBuffer()
  {
    return _buffer;
  }

  /**
   * Returns the number of rows updated.
   */
  public int getRowUpdateCount()
  {
    return _rowUpdateCount;
  }

  /**
   * Sets the number of rows updated.
   */
  public void setRowUpdateCount(int count)
  {
    _rowUpdateCount = count;
  }

  /**
   * Set if the query should return the generated keys.
   */
  public boolean isReturnGeneratedKeys()
  {
    return _isReturnGeneratedKeys;
  }

  /**
   * Set if the query should return the generated keys.
   */
  public void setReturnGeneratedKeys(boolean isReturnGeneratedKeys)
  {
    _isReturnGeneratedKeys = isReturnGeneratedKeys;
    
    if (_isReturnGeneratedKeys && _generatedKeys != null)
      _generatedKeys.init();
  }

  /**
   * Sets the indexed group field.
   */
  public boolean isGroupNull(int index)
  {
    return _groupItem.isNull(index);
  }

  /**
   * Sets the indexed group field.
   */
  public void setGroupString(int index, String value)
  {
    _groupItem.setString(index, value);
  }

  /**
   * Sets the indexed group field.
   */
  public String getGroupString(int index)
  {
    return _groupItem.getString(index);
  }

  /**
   * Sets the indexed group field as a long.
   */
  public void setGroupLong(int index, long value)
  {
    _groupItem.setLong(index, value);
  }

  /**
   * Sets the indexed group field as a long.
   */
  public long getGroupLong(int index)
  {
    return _groupItem.getLong(index);
  }

  /**
   * Sets the indexed group field as a double.
   */
  public void setGroupDouble(int index, double value)
  {
    _groupItem.setDouble(index, value);
  }

  /**
   * Sets the indexed group field as a double.
   */
  public double getGroupDouble(int index)
  {
    return _groupItem.getDouble(index);
  }

  /**
   * Returns the indexed group field.
   */
  public Data getGroupData(int index)
  {
    return _groupItem.getData(index);
  }

  /**
   * Set a null parameter.
   */
  public void setNull(int index)
  {
    _parameters[index].setString(null);
  }

  /**
   * Returns the null parameter.
   */
  public boolean isNull(int index)
  {
    return _parameters[index].isNull();
  }

  /**
   * Set a long parameter.
   */
  public void setLong(int index, long value)
  {
    _parameters[index].setLong(value);
  }

  /**
   * Returns the boolean parameter.
   */
  public int getBoolean(int index)
  {
    return _parameters[index].getBoolean();
  }

  /**
   * Set a boolean parameter.
   */
  public void setBoolean(int index, boolean value)
  {
    _parameters[index].setBoolean(value);
  }

  /**
   * Returns the long parameter.
   */
  public long getLong(int index)
  {
    return _parameters[index].getLong();
  }

  /**
   * Returns the long parameter.
   */
  public long getDate(int index)
  {
    return _parameters[index].getDate();
  }

  /**
   * Set a double parameter.
   */
  public void setDouble(int index, double value)
  {
    _parameters[index].setDouble(value);
  }

  /**
   * Returns the double parameter.
   */
  public double getDouble(int index)
  {
    return _parameters[index].getDouble();
  }

  /**
   * Set a string parameter.
   */
  public void setString(int index, String value)
  {
    _parameters[index].setString(value);
  }

  /**
   * Returns the string parameter.
   */
  public String getString(int index)
  {
    return _parameters[index].getString();
  }

  /**
   * Sets the result set.
   */
  public void setResult(SelectResult result)
  {
    _result = result;
  }

  /**
   * Gets the result set.
   */
  public SelectResult getResult()
  {
    return _result;
  }

  /**
   * Gets the generated keys result set.
   */
  public GeneratedKeysResultSet getGeneratedKeysResultSet()
  {
    if (! _isReturnGeneratedKeys)
      return null;

    if (_generatedKeys == null)
      _generatedKeys = new GeneratedKeysResultSet();
    
    return _generatedKeys;
  }

  /**
   * Returns a new query context.
   */
  public static void free(QueryContext queryContext)
  {
    queryContext._groupMap = null;
    
    _freeList.free(queryContext);
  }
}
