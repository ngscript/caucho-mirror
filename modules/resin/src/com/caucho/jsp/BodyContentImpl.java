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

package com.caucho.jsp;

import java.io.*;
import java.util.*;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;

import com.caucho.log.Log;

import com.caucho.util.CharBuffer;
import com.caucho.util.L10N;
import com.caucho.util.FreeList;

import com.caucho.vfs.ReadStream;
import com.caucho.vfs.WriteStream;
import com.caucho.vfs.TempCharStream;
import com.caucho.vfs.TempCharBuffer;
import com.caucho.vfs.TempCharReader;

/**
 * Implementation of the JSP BodyContent interface.
 */
public class BodyContentImpl extends AbstractBodyContent {
  static final L10N L = new L10N(BodyContentImpl.class);
  static final Logger log = Log.open(BodyContentImpl.class);
  
  private static final FreeList<BodyContentImpl> _freeList
    = new FreeList<BodyContentImpl>(32);

  private TempCharStream _tempStream = new TempCharStream();
  private TempCharReader _charReader;
  private JspPrintWriter _printWriter;
  private JspWriter _prev;

  /**
   * Creates a new QBodyContent instance.
   *
   * @param prev the encloding writer.
   */
  private BodyContentImpl(JspWriter prev)
  {
    setParent(prev);
  }

  /**
   * Allocates a new BodyContent instance.
   */
  static BodyContentImpl allocate()
  {
    BodyContentImpl body = (BodyContentImpl) _freeList.allocate();
    
    if (body == null)
      body = new BodyContentImpl(null);

    return body;
  }

  /**
   * Initializes the BodyContent object.
   *
   * @param prev the enclosing writer
   */
  void init(JspWriter prev)
  {
    setParent(prev);

    _tempStream.openWrite();
  }

  /**
   * Writes characters to the stream.
   *
   * @param buf character buffer
   * @param off starting offset into the buffer
   * @param len length of valid bytes in the buffer.
   */
  final public void write(char []buf, int off, int len) throws IOException
  {
    _tempStream.write(buf, off, len);
  }

  /**
   * Writes characters to the stream.
   *
   * @param s string
   * @param off starting offset into the buffer
   * @param len length of valid bytes in the buffer.
   */
  final public void write(String s, int off, int len) throws IOException
  {
    _tempStream.write(s, off, len);
  }

  /**
   * Writes characters to the stream.
   *
   * @param ch character to write.
   */
  final public void write(int ch) throws IOException
  {
    _tempStream.write(ch);
  }

  final public void clear() throws IOException
  {
    _tempStream.clearWrite();
  }

  final public void clearBuffer() throws IOException
  {
    clear();
  }

  final public void flush() throws IOException
  {
  }

  final public void close() throws IOException
  {
  }

  final public int getBufferSize()
  {
    return -1;
  }

  final public int getRemaining()
  {
    return 0;
  }

  /**
   * Clears the body contents.
   */
  public void clearBody()
  {
    _tempStream.clearWrite();
  }

  /**
   * Returns a reader to the body content.
   */
  public Reader getReader()
  {
    _charReader = new TempCharReader();
    _charReader.init(_tempStream.getHead());
    
    return _charReader;
  }

  public CharBuffer getCharBuffer()
  {
    CharBuffer cb = new CharBuffer();
      
    TempCharBuffer head = _tempStream.getHead();

    for (; head != null; head = head.getNext()) {
      char []cbuf = head.getBuffer();

      cb.append(cbuf, 0, head.getLength());
    }
      
    return cb;
  }

  /**
   * Returns a string representing the body content.
   */
  public String getString()
  {
    TempCharBuffer head = _tempStream.getHead();

    if (head == null)
      return "";

    if (head.getNext() == null)
      return new String(head.getBuffer(), 0, head.getLength());

    int length = 0;
    for (; head != null; head = head.getNext())
      length += head.getLength();

    char []buf = new char[length];

    int offset = 0;
    for (head = _tempStream.getHead(); head != null; head = head.getNext()) {
      char []cbuf = head.getBuffer();
      int sublen = head.getLength();

      System.arraycopy(cbuf, 0, buf, offset, sublen);

      offset += sublen;
    }
      
    return new String(buf, 0, length);
  }

  /**
   * Returns a string representing the body content.
   */
  public String getTrimString()
  {
    TempCharBuffer head = _tempStream.getHead();

    boolean hasData = false;

    char []buf = null;
    int totalLength = 0;
    for (; head != null; head = head.getNext()) {
      char []cbuf = head.getBuffer();
      int end = head.getLength();
      int offset = 0;

      if (! hasData) {
	for (offset = 0; offset < end; offset++) {
	  if (! Character.isWhitespace(cbuf[offset])) {
	    hasData = true;
	    break;
	  }
	}
      }

      if (head.getNext() == null) {
	for (; offset < end; end--) {
	  if (! Character.isWhitespace(cbuf[end - 1]))
	    break;
	}

	if (buf != null) {
	  System.arraycopy(cbuf, offset, buf, totalLength, end - offset);
	  totalLength += end - offset;
	  
	  return new String(buf, 0, totalLength);
	}
	else if (offset == end)
	  return "";
	else
	  return new String(cbuf, offset, end - offset);
	
      }
      else if (buf == null) {
	int length = 0;

	for (TempCharBuffer ptr = head; ptr != null; ptr = ptr.getNext())
	  length += ptr.getLength();

	buf = new char[length];

	System.arraycopy(cbuf, offset, buf, 0, end - offset);
	totalLength += end - offset;
      }
      else {
	System.arraycopy(cbuf, offset, buf, totalLength, end - offset);
	totalLength += end - offset;
      }
    }

    return "";
  }

  /**
   * Writes the body contents out to the named writer.
   */
  public void writeOut(Writer out) throws IOException
  {
    try {
      TempCharBuffer head = _tempStream.getHead();

      for (; head != null; head = head.getNext()) {
	char []cbuf = head.getBuffer();

	out.write(cbuf, 0, head.getLength());
      }
    } catch (IOException e) {
    }
  }

  /**
   * Writes the body contents out to the named writer.
   */
  public void flushBuffer() throws IOException
  {
  }

  /**
   * Returns the print writer.
   */
  public PrintWriter getWriter()
  {
    if (_printWriter == null)
      _printWriter = new JspPrintWriter(this);

    _printWriter.init(this);

    return _printWriter;
  }
  
  /**
   * Releases the body content at the end of the tag.
   */
  public void release()
  {
    releaseNoFree();

    _freeList.free(this);
  }
  
  void releaseNoFree()
  {
    if (_charReader != null && ! _charReader.isEmpty()) {
      _charReader.setFree(true);
      _tempStream.discard();
    }
    else
      _tempStream.destroy();

    _charReader = null;
    _prev = null;
  }

  AbstractJspWriter popWriter()
  {
    AbstractJspWriter parent = super.popWriter();
    
    release();

    return parent;
  }
}
