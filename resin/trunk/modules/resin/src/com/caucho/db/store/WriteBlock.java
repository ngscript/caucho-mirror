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

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.IOException;

import com.caucho.util.L10N;
import com.caucho.util.ClockCacheItem;

import com.caucho.vfs.TempBuffer;

import com.caucho.log.Log;

/**
 * Represents a write (dirty) block.
 */
abstract public class WriteBlock extends Block {
  private static final Logger log = Log.open(WriteBlock.class);
  private static final L10N L = new L10N(WriteBlock.class);

  protected Block _block;

  public WriteBlock(Block block)
    throws IOException
  {
    super(block.getStore(), block.getBlockId());

    _block = block;
  }

  /* XXX: need this?
  public void setDirty(int min, int max)
  {
  }
  */

  /**
   * Frees a block from a query.
   */
  public void free()
  {
  }

  /**
   * Closes the write block.
   */
  void destroy()
  {
    Block block = _block;
    _block = block;

    block.free();

    close();
  }

  public String toString()
  {
    return "WriteBlock[" + getStore() + "," + getBlockId() / Store.BLOCK_SIZE + "]";
  }
}
