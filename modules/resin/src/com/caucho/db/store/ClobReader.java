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

package com.caucho.db.store;

import java.io.IOException;
import java.io.Reader;

import java.sql.SQLException;

import com.caucho.vfs.WriteStream;

import com.caucho.util.CharBuffer;

import com.caucho.db.sql.Expr;

public class ClobReader extends Reader {
  private static final int INODE_DIRECT_BLOCKS = 14;
    
  private Store _store;

  private long _length;
  private long _offset;

  private byte []_inode;
  private int _inodeOffset;

  private long _lastOffset;
  private long _fragmentId;

  private char []_buffer;

  /**
   * Creates a clob reader.
   *
   * @param store the backing store
   */
  public ClobReader(Store store, byte []inode, int inodeOffset)
  {
    init(store, inode, inodeOffset);
  }
  
  /**
   * Creates a clob reader.
   *
   * @param store the backing store
   */
  public ClobReader(Inode inode)
  {
    init(inode.getStore(), inode.getBuffer(), 0);
  }

  /**
   * Initialize the output stream.
   */
  public void init(Store store, byte []inode, int inodeOffset)
  {
    if (store == null)
      throw new NullPointerException();
    
    _store = store;

    _inode = inode;
    _inodeOffset = inodeOffset;

    _length = readLong(inode, inodeOffset);
    _offset = 0;

    _fragmentId = 0;
    _lastOffset = 0;
  }

  /**
   * Reads a char.
   */
  public int read()
    throws IOException
  {
    if (_buffer == null)
      _buffer = new char[1];

    int len = read(_buffer, 0, 1);

    if (len < 0)
      return -1;
    else
      return _buffer[0];
  }

  /**
   * Reads a buffer.
   */
  public int read(char []buf, int offset, int length)
    throws IOException
  {
    if (_length <= _offset)
      return -1;

    if (_length <= Inode.INLINE_BLOB_SIZE) {
      if (_length - _offset < 2 * length)
	length = (int) (_length - _offset) >> 1;

      for (int i = 0; i < length; i++) {
	int ch1 = _inode[_inodeOffset + 8 + (int) _offset] & 0xff;
	int ch2 = _inode[_inodeOffset + 9 + (int) _offset] & 0xff;
	
	buf[offset + i] = (char) ((ch1 << 8) + ch2);

	_offset += 2;
      }

      return length;
    }

    long fragAddr = _fragmentId;

    // cache the last fragment id
    if (fragAddr == 0 ||
	_lastOffset / Inode.INODE_BLOCK_SIZE !=
	_offset / Inode.INODE_BLOCK_SIZE) {
      int count = (int) _offset / Inode.INODE_BLOCK_SIZE;
      
      fragAddr = Inode.readFragmentAddr(count, _inode, _inodeOffset, _store);

      _lastOffset = _offset;
      _fragmentId = fragAddr;
    }

    int fragOffset = (int) _offset % Inode.INODE_BLOCK_SIZE;

    int len = _store.readFragment(fragAddr, fragOffset, buf, offset, length);

    if (len > 0)
      _offset += 2 * len;

    return len;
  }

  /**
   * Closes the buffer.
   */
  public void close()
  {
  }

  /**
   * Writes the long.
   */
  public static long readLong(byte []buffer, int offset)
  {
    return (((buffer[offset + 0] & 0xffL) << 56) +
	    ((buffer[offset + 1] & 0xffL) << 48) +
	    ((buffer[offset + 2] & 0xffL) << 40) +
	    ((buffer[offset + 3] & 0xffL) << 32) +
	    ((buffer[offset + 4] & 0xffL) << 24) +
	    ((buffer[offset + 5] & 0xffL) << 16) +
	    ((buffer[offset + 6] & 0xffL) << 8) +
	    ((buffer[offset + 7] & 0xffL)));
  }

  /**
   * Writes the short.
   */
  private static int readShort(byte []buffer, int offset)
  {
    return (((buffer[offset + 0] & 0xff) << 8) +
	    ((buffer[offset + 1] & 0xff)));
  }
}
