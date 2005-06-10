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

package com.caucho.db.table;

import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;

import java.util.ArrayList;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.sql.SQLException;

import com.caucho.util.CharBuffer;
import com.caucho.util.L10N;

import com.caucho.vfs.Path;
import com.caucho.vfs.WriteStream;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.TempBuffer;
import com.caucho.vfs.TempStream;

import com.caucho.sql.SQLExceptionWrapper;

import com.caucho.log.Log;

import com.caucho.db.Database;

import com.caucho.db.store.Store;
import com.caucho.db.store.Block;
import com.caucho.db.store.WriteBlock;
import com.caucho.db.store.BlockManager;
import com.caucho.db.store.Transaction;
import com.caucho.db.store.Lock;

import com.caucho.db.index.BTree;
import com.caucho.db.index.KeyCompare;

import com.caucho.db.sql.Expr;
import com.caucho.db.sql.Parser;
import com.caucho.db.sql.CreateQuery;
import com.caucho.db.sql.QueryContext;

/**
 * Table format:
 *
 * <pre>
 * Block 0: allocation
 * Block 1: table definition
 *   0    - store data
 *   1024 - table data
 *    1024 - index pointers
 *   2048 - CREATE text
 * Block 2: first data
 * </pre>
 */
public class Table extends Store {
  private final static Logger log = Log.open(Table.class);
  private final static L10N L = new L10N(Table.class);

  private final static int ROOT_DATA_OFFSET = STORE_CREATE_END;
  private final static int INDEX_ROOT_OFFSET = ROOT_DATA_OFFSET + 32;
  
  private final static int ROOT_DATA_END = ROOT_DATA_OFFSET + 1024;
  
  public final static int INLINE_BLOB_SIZE = 120;
  
  public final static long ROW_CLOCK_MIN = 1024;
  
  private final Row _row;

  private final int _rowLength;
  private final int _rowsPerBlock;
  private final int _rowEnd;

  private final Constraint[]_constraints;
  
  private final Column _autoIncrementColumn;

  private long _entries;

  private long _rowClockAddr;
  private long _rowClockTotal;
  private long _rowClockUsed;

  private long _autoIncrementValue = -1;

  Table(Database database, String name, Row row, Constraint constraints[])
  {
    super(database, name, null);
    
    _row = row;
    _constraints = constraints;

    _rowLength = _row.getLength();
    _rowsPerBlock = BLOCK_SIZE / _rowLength;
    _rowEnd = _rowLength * _rowsPerBlock;

    _rowClockAddr = 0;
    
    Column []columns = _row.getColumns();
    Column autoIncrementColumn = null;
    for (int i = 0; i < columns.length; i++) {
      columns[i].setTable(this);
      
      if (columns[i].getAutoIncrement() >= 0)
	autoIncrementColumn = columns[i];
    }
    _autoIncrementColumn = autoIncrementColumn;
  }

  Row getRow()
  {
    return _row;
  }

  /**
   * Returns the length of a row.
   */
  int getRowLength()
  {
    return _rowLength;
  }

  /**
   * Returns the end of the row
   */
  int getRowEnd()
  {
    return _rowEnd;
  }
  
  public final Column []getColumns()
  {
    return _row.getColumns();
  }

  /**
   * Returns the table's constraints.
   */
  public final Constraint []getConstraints()
  {
    return _constraints;
  }

  /**
   * Returns the auto-increment column.
   */
  public Column getAutoIncrementColumn()
  {
    return _autoIncrementColumn;
  }

  /**
   * Returns the column for the given column name.
   *
   * @param name the column name
   *
   * @return the column
   */
  public Column getColumn(String name)
  {
    Column []columns = getColumns();

    for (int i = 0; i < columns.length; i++) {
      if (columns[i].getName().equals(name))
	return columns[i];
    }

    return null;
  }

  /**
   * Returns the column index for the given column name.
   *
   * @param name the column name
   *
   * @return the column index.
   */
  public int getColumnIndex(String name)
    throws SQLException
  {
    Column []columns = getColumns();

    for (int i = 0; i < columns.length; i++) {
      if (columns[i].getName().equals(name))
	return i;
    }

    return -1;
  }

  /**
   * Loads the table from the file.
   */
  public static Table loadFromFile(Database db, String name)
    throws IOException, SQLException
  {
    Path path = db.getPath().lookup(name + ".db");

    if (! path.exists())
      throw new IOException(L.l("table {0} does not exist", name));

    ReadStream is = path.openRead();
    try {
      is.skip(BLOCK_SIZE + ROOT_DATA_END);

      CharBuffer cb = new CharBuffer();

      int ch;
      while ((ch = is.read()) > 0) {
	cb.append((char) ch);
      }

      String sql = cb.toString();

      if (log.isLoggable(Level.FINER))
	log.finer("Table[" + name + "] " + sql);

      try {
	CreateQuery query = (CreateQuery) Parser.parse(db, sql);

	TableFactory factory = query.getFactory();

	if (! factory.getName().equalsIgnoreCase(name))
	  throw new IOException(L.l("factory {0} does not match", name));
	
	Table table = new Table(db, factory.getName(), factory.getRow(),
				factory.getConstraints());

	table.init();

	table.initIndexes();
	// table._blockMax = path.getLength() / BLOCK_SIZE;
      
	return table;
      } catch (Throwable e) {
	log.warning(e.toString());

	throw new SQLException(L.l("can't load table {0} in {1}.\n{2}",
				   name, path.getNativePath(), e.toString()));
      }
    } finally {
      is.close();
    }
  }

  /**
   * Creates the table.
   */
  public void create()
    throws IOException, SQLException
  {
    super.create();
    
    Column []columns = _row.getColumns();
    for (int i = 0; i < columns.length; i++) {
      Column column = columns[i];

      if (! column.isUnique())
	continue;

      KeyCompare keyCompare = column.getIndexKeyCompare();

      if (keyCompare == null)
	continue;

      Block rootBlock = allocate();
      long rootBlockId = rootBlock.getBlockId();
      rootBlock.free();

      BTree btree = new BTree(this, rootBlockId, column.getLength(),
			      keyCompare);

      column.setIndex(btree);
    }

    byte []tempBuffer = new byte[BLOCK_SIZE];

    readBlock(BLOCK_SIZE, tempBuffer, 0, BLOCK_SIZE);

    TempStream ts = new TempStream();
      
    WriteStream os = new WriteStream(ts);

    try {
      for (int i = 0; i < ROOT_DATA_OFFSET; i++)
	os.write(tempBuffer[i]);
      
      writeTableHeader(os);
    } finally {
      os.close();
    }

    TempBuffer head = ts.getHead();
    int offset = 0;
    for (; head != null; head = head.getNext()) {
      byte []buffer = head.getBuffer();
      
      int length = head.getLength();
      
      System.arraycopy(buffer, 0, tempBuffer, offset, length);

      for (; length < buffer.length; length++) {
	tempBuffer[offset + length] = 0;
      }

      offset += buffer.length;
    }

    for (; offset < BLOCK_SIZE; offset++)
      tempBuffer[offset] = 0;

    writeBlock(BLOCK_SIZE, tempBuffer, 0, BLOCK_SIZE);

    _database.addTable(this);
  }
  
  /**
   * Initialize the indexes
   */
  private void initIndexes()
    throws IOException, SQLException
  {
    Block block = readBlock(addressToBlockId(BLOCK_SIZE));

    try {
      byte []buffer = block.getBuffer();

      int indexCount = 0;
      Column []columns = _row.getColumns();
      for (int i = 0; i < columns.length; i++) {
	Column column = columns[i];

	if (! column.isUnique())
	  continue;

	if (column.getIndexKeyCompare() == null)
	  continue;
	
	long indexRoot = getLong(buffer, INDEX_ROOT_OFFSET + indexCount * 8);

	indexCount++;

	if (indexRoot > 0) {
	  if (column.getIndexKeyCompare() == null)
	    throw new SQLException(L.l("The database index for '{0}' is broken for column {1}",
				       getName(),
				       column));
	  
	  column.setIndex(new BTree(this, indexRoot,
				    column.getLength(),
				    column.getIndexKeyCompare()));
	}
      }
    } finally {
      block.free();
    }
  }

  private void writeTableHeader(WriteStream os)
    throws IOException
  {
    os.print("Resin-DB 03.11.02");
    os.write(0);

    while (os.getBufferOffset() < INDEX_ROOT_OFFSET)
      os.write(0);

    Column []columns = _row.getColumns();
    for (int i = 0; i < columns.length; i++) {
      if (! columns[i].isUnique())
	continue;

      BTree index = columns[i].getIndex();

      if (index != null) {
	writeLong(os, index.getIndexRoot());
      }
      else {
	writeLong(os, 0);
      }
    }

    while (os.getBufferOffset() < ROOT_DATA_END)
      os.write(0);

    os.print("CREATE TABLE " + getName() + "(");
    for (int i = 0; i < _row.getColumns().length; i++) {
      Column column = _row.getColumns()[i];
      
      if (i != 0)
	os.print(",");
      
      os.print(column.getName());
      os.print(" ");

      switch (column.getTypeCode()) {
      case Column.VARCHAR:
	os.print("VARCHAR(" + column.getDeclarationSize() + ")");
	break;
      case Column.INT:
	os.print("INTEGER");
	break;
      case Column.LONG:
	os.print("BIGINT");
	break;
      case Column.DOUBLE:
	os.print("DOUBLE");
	break;
      case Column.DATE:
	os.print("TIMESTAMP");
	break;
      case Column.BLOB:
	os.print("BLOB");
	break;
      case Column.NUMERIC:
	{
	  NumericColumn numeric = (NumericColumn) column;
	  
	  os.print("NUMERIC(" + numeric.getPrecision() + "," + numeric.getScale() + ")");
	  break;
	}
      default:
	throw new UnsupportedOperationException();
      }

      if (column.isPrimaryKey())
	os.print(" PRIMARY KEY");
      else if (column.isUnique())
	os.print(" UNIQUE");
      
      if (column.isNotNull())
	os.print(" NOT NULL");

      Expr defaultExpr = column.getDefault();
      
      if (defaultExpr != null) {
	os.print(" DEFAULT (");
	os.print(defaultExpr);
	os.print(")");
      }

      if (column.getAutoIncrement() >= 0)
	os.print(" auto_increment");
    }
    os.print(")");

    /*
    writeLong(os, _blockMax);
    writeLong(os, _entries);
    writeLong(os, _clockAddr);
    */
  }

  public TableIterator createTableIterator()
  {
    return new TableIterator(this);
  }

  /**
   * Returns the next auto-increment value.
   */
  public long nextAutoIncrement(QueryContext context)
    throws SQLException
  {
    synchronized (this) {
      if (_autoIncrementValue >= 0)
	return ++_autoIncrementValue;
    }

    long max = 0;
    
    try {
      TableIterator iter = createTableIterator();
      iter.init(context);
      while (iter.next()) {
	iter.prevRow();

	byte []buffer = iter.getBuffer();
      
	while (iter.nextRow()) {
	  long value = _autoIncrementColumn.getLong(buffer, iter.getRowOffset());

	  if (max < value)
	    max = value;
	}
      }
    } catch (IOException e) {
      throw new SQLExceptionWrapper(e);
    }

    synchronized (this) {
      if (_autoIncrementValue < max)
	_autoIncrementValue = max;

      return ++_autoIncrementValue;
    }
  }

  /**
   * Inserts a new row, returning the row address.
   */
  public long insert(QueryContext queryContext, Transaction xa,
		     ArrayList<Column> columns,
		     ArrayList<Expr> values)
    throws IOException, SQLException
  {
    if (log.isLoggable(Level.FINE))
      log.fine("db table " + getName() + " insert row xa:" + xa);

    TableIterator iter = createTableIterator();
    TableIterator []iterSet = new TableIterator[] { iter };
    // QueryContext context = QueryContext.allocate();
    queryContext.init(xa, iterSet);
    iter.init(queryContext);

    boolean isLoop = false;

    while (true) {
      Block block = null;

      try {
	long addr = _rowClockAddr;
	
	long blockId = firstRow(addr);

	int rowOffset;

	if (blockId < 0) {
	  // go around loop if there are sufficient entries, i.e. over
	  // ROW_CLOCK_MIN and at least 1/4 free entries.
	  if (! isLoop &&
	      ROW_CLOCK_MIN < _rowClockTotal &&
	      4 * _rowClockUsed < 3 * _rowClockTotal) {
	    isLoop = true;
	    _rowClockAddr = 0;
	    _rowClockUsed = 0;
	    _rowClockTotal = 0;
	    continue;
	  }
	  else {
	    // if no free row is available, allocate a new one
	    block = xa.allocateRow(this);

	    blockId = block.getBlockId();

	    addr = blockIdToAddress(blockId);

	    _rowClockAddr = 0;
	    _rowClockUsed = 0;
	    _rowClockTotal = 0;
	    
	    rowOffset = 0;
	  }
	}
	else if (blockId == addressToBlockId(addr)) {
	  rowOffset = (int) (addr & BLOCK_INDEX_MASK);
	}
	else {
	  rowOffset = 0;
	  _rowClockAddr = blockIdToAddress(blockId);
	  addr = _rowClockAddr;
	}

	long nextRowOffset = rowOffset + _rowLength;
      
	if (_rowEnd <= nextRowOffset)
	  nextRowOffset = BLOCK_SIZE;

	_rowClockAddr = (_rowClockAddr & ~BLOCK_INDEX_MASK) + nextRowOffset;
	_rowClockTotal++;
	
	if (block == null)
	  block = xa.readBlock(this, blockId);

	if ((block.getBuffer()[rowOffset] & 0x01) == 1) {
	  _rowClockUsed++;
	  continue;
	}

	WriteBlock writeBlock = xa.createWriteBlock(block);
	block = writeBlock;

	byte []buffer = writeBlock.getBuffer();

	long rowAddr = blockIdToAddress(writeBlock.getBlockId(), rowOffset);

	boolean isOkay = false;
	try {
	  iter.setRow(writeBlock, rowOffset);

	  for (int i = 0; i < columns.size(); i++) {
	    Column column = columns.get(i);
	    Expr value = values.get(i);

	    column.setExpr(xa, buffer, rowOffset, value, queryContext);
	  }
    
	  buffer[rowOffset] |= 1;
      
	  validate(writeBlock, rowOffset, queryContext, xa);

	  for (int i = 0; i < columns.size(); i++) {
	    Column column = columns.get(i);
	    Expr value = values.get(i);

	    column.setIndex(xa, buffer, rowOffset, rowAddr, queryContext);
	  }

	  if (_autoIncrementColumn != null) {
	    long value = _autoIncrementColumn.getLong(buffer, rowOffset);

	    if (_autoIncrementValue < value)
	      _autoIncrementValue = value;
	  }
	  isOkay = true;
	} finally {
	  if (! isOkay)
	    buffer[rowOffset] &= ~1;
	}
    
	_entries++;

	return addr;
      } finally {
	if (block != null)
	  block.free();
      }
    }
  }

  /**
   * Validates the given row.
   */
  private void validate(Block block, int rowOffset,
			QueryContext queryContext, Transaction xa)
    throws SQLException
  {
    TableIterator row = createTableIterator();
    TableIterator []rows = new TableIterator[] { row };

    row.setRow(block, rowOffset);

    for (int i = 0; i < _constraints.length; i++) {
      _constraints[i].validate(rows, queryContext, xa);
    }
  }
  
  void delete(Transaction xa, byte []block, int rowOffset)
    throws SQLException
  {
    Column []columns = _row.getColumns();
    
    for (int i = 0; i < columns.length; i++)
      columns[i].delete(xa, block, rowOffset);
    
    block[rowOffset] = 0;
  }

  private void writeLong(WriteStream os, long value)
    throws IOException
  {
    os.write((int) (value >> 56));
    os.write((int) (value >> 48));
    os.write((int) (value >> 40));
    os.write((int) (value >> 32));
    os.write((int) (value >> 24));
    os.write((int) (value >> 16));
    os.write((int) (value >> 8));
    os.write((int) value);
  }

  private void setLong(byte []buffer, int offset, long value)
    throws IOException
  {
    buffer[offset + 0] = (byte) (value >> 56);
    buffer[offset + 1] = (byte) (value >> 48);
    buffer[offset + 2] = (byte) (value >> 40);
    buffer[offset + 3] = (byte) (value >> 32);
    buffer[offset + 4] = (byte) (value >> 24);
    buffer[offset + 5] = (byte) (value >> 16);
    buffer[offset + 6] = (byte) (value >> 8);
    buffer[offset + 7] = (byte) (value);
  }

  private long getLong(byte []buffer, int offset)
    throws IOException
  {
    long value = (((buffer[offset + 0] & 0xffL) << 56) +
		  ((buffer[offset + 1] & 0xffL) << 48) +
		  ((buffer[offset + 2] & 0xffL) << 40) +
		  ((buffer[offset + 3] & 0xffL) << 32) +

		  ((buffer[offset + 4] & 0xffL) << 24) +
		  ((buffer[offset + 5] & 0xffL) << 16) +
		  ((buffer[offset + 6] & 0xffL) << 8) +
		  ((buffer[offset + 7] & 0xffL)));

    return value;
  }
  
  public String toString()
  {
    return "Table[" + getName() + ":" + getId() + "]";
  }
}
