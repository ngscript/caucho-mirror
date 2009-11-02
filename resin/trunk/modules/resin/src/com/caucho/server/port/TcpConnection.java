/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is finishThread software; you can redistribute it and/or modify
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

package com.caucho.server.port;

import com.caucho.server.connection.*;
import com.caucho.loader.Environment;
import com.caucho.management.server.AbstractManagedObject;
import com.caucho.management.server.TcpConnectionMXBean;
import com.caucho.server.connection.*;
import com.caucho.server.resin.Resin;
import com.caucho.server.util.CauchoSystem;
import com.caucho.server.webapp.WebApp;
import com.caucho.servlet.DuplexListener;
import com.caucho.util.Alarm;
import com.caucho.util.L10N;
import com.caucho.vfs.ClientDisconnectException;
import com.caucho.vfs.QSocket;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.WriteStream;
import com.caucho.vfs.StreamImpl;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * A protocol-independent TcpConnection.  TcpConnection controls the
 * TCP Socket and provides buffered streams.
 *
 * <p>Each TcpConnection has its own thread.
 */
public class TcpConnection extends Connection
{
  private static final L10N L = new L10N(TcpConnection.class);
  private static final Logger log
    = Logger.getLogger(TcpConnection.class.getName());

  private static final ThreadLocal<ServerRequest> _currentRequest
    = new ThreadLocal<ServerRequest>();

  private static final AtomicInteger _connectionCount = new AtomicInteger();

  private static ClassLoader _systemClassLoader;

  private final int _connectionId;  // The connection's id
  private final String _id;
  private final String _name;
  private String _dbgId;

  private final Port _port;
  private final QSocket _socket;
  private final ServerRequest _request;
  private final byte []_testBuffer = new byte[1];

  private final AcceptTask _acceptTask = new AcceptTask();
  private final KeepaliveRequestTask _keepaliveRequestTask
    = new KeepaliveRequestTask();
  private final ResumeTask _resumeTask = new ResumeTask();

  private final Admin _admin = new Admin();

  private ConnectionState _state = ConnectionState.IDLE;
  private ConnectionController _controller;
  private boolean _isKeepalive;
  private boolean _isWake;

  private boolean _isClosed;

  private ConnectionReadTask _keepaliveTask = _keepaliveRequestTask;

  private long _idleTimeout;

  private long _connectionStartTime;
  private long _requestStartTime;

  private long _idleStartTime;
  private long _idleExpireTime;

  // statistics state
  private String _displayState;

  private Thread _thread;

  public boolean _isFree;

  private Exception _startThread;

  /**
   * Creates a new TcpConnection.
   *
   * @param server The TCP server controlling the connections
   * @param request The protocol Request
   */
  TcpConnection(Port port,
                QSocket socket)
  {
    _connectionId = _connectionCount.incrementAndGet();

    _port = port;
    _socket = socket;

    int id = getId();

    Protocol protocol = port.getProtocol();

    _request = protocol.createRequest(this);

    String protocolName = protocol.getProtocolName();

    if (port.getAddress() == null) {
      Resin resin = Resin.getLocal();
      String serverId = resin != null ? resin.getServerId() : null;
      if (serverId == null)
        serverId = "";

      _id = protocolName + "-" + serverId + "-" + port.getPort() + "-" + id;
      _name = protocolName + "-" + port.getPort() + "-" + id;
    }
    else {
      _id = (protocolName + "-" + port.getAddress() + ":" +
             port.getPort() + "-" + id);
      _name = (protocolName + "-" + port.getAddress() + "-" +
               port.getPort() + "-" + id);
    }
  }

  /**
   * Returns the ServerRequest for the current thread.
   */
  public static ServerRequest getCurrentRequest()
  {
    return _currentRequest.get();
  }

  /**
   * For QA only, set the current request.
   */
  public static void qaSetCurrentRequest(ServerRequest request)
  {
    _currentRequest.set(request);
  }

  /**
   * Returns the connection id.  Primarily for debugging.
   */
  public int getId()
  {
    return _connectionId;
  }

  /**
   * Returns the object name for jmx.
   */
  public String getName()
  {
    return _name;
  }

  /**
   * Returns the port which generated the connection.
   */
  public Port getPort()
  {
    return _port;
  }

  /**
   * Returns the request for the connection.
   */
  public final ServerRequest getRequest()
  {
    return _request;
  }

  /**
   * Returns the admin
   */
  public TcpConnectionMXBean getAdmin()
  {
    return _admin;
  }

  //
  // timeout properties
  //

  /**
   * Sets the idle time for a keepalive connection.
   */
  public void setIdleTimeout(long idleTimeout)
  {
    _idleTimeout = idleTimeout;
  }

  /**
   * The idle time for a keepalive connection
   */
  public long getIdleTimeout()
  {
    return _idleTimeout;
  }

  //
  // state information
  //

  /**
   * Returns true for active.
   */
  public boolean isActive()
  {
    return _state.isActive();
  }

  /**
   * Returns true for active.
   */
  public boolean isRequestActive()
  {
    return _state.isRequestActive();
  }

  /**
   * Returns true for closed.
   */
  public boolean isClosed()
  {
    return _isClosed;
  }

  /**
   * Returns the state string.
   */
  public final String getState()
  {
    return _state.toString();
  }

  public final boolean isDestroyed()
  {
    return _state.isDestroyed();
  }

  @Override
  public boolean isKeepalive()
  {
    return _isKeepalive;
  }

  @Override
  public boolean isComet()
  {
    return _state.isComet();
  }

  public boolean isSuspend()
  {
    return _controller != null && _controller.isSuspended();
  }

  @Override
  public boolean isDuplex()
  {
    return _state.isDuplex();
  }

  public boolean isWake()
  {
    return _isWake;
  }

  //
  // port/socket information
  //

  /**
   * Returns the connection's socket
   */
  public QSocket getSocket()
  {
    return _socket;
  }

  /**
   * Returns the local address of the socket.
   */
  public InetAddress getLocalAddress()
  {
    return _socket.getLocalAddress();
  }

  /**
   * Returns the socket's local TCP port.
   */
  public int getLocalPort()
  {
    return _socket.getLocalPort();
  }

  /**
   * Returns the socket's remote address.
   */
  public InetAddress getRemoteAddress()
  {
    return _socket.getRemoteAddress();
  }

  /**
   * Returns the socket's remote host name.
   */
  @Override
  public String getRemoteHost()
  {
    return _socket.getRemoteHost();
  }

  /**
   * Adds from the socket's remote address.
   */
  @Override
  public int getRemoteAddress(byte []buffer, int offset, int length)
  {
    return _socket.getRemoteAddress(buffer, offset, length);
  }

  /**
   * Returns the socket's remote port
   */
  public int getRemotePort()
  {
    return _socket.getRemotePort();
  }

  /**
   * Returns true if the connection is secure, i.e. a SSL connection
   */
  @Override
  public boolean isSecure()
  {
    if (_isClosed)
      return false;
    else
      return _socket.isSecure() || _port.isSecure();
  }

  /**
   * Returns the virtual host.
   */
  @Override
  public String getVirtualHost()
  {
    return getPort().getVirtualHost();
  }

  //
  // thread information
  //

  /**
   * Returns the thread id.
   */
  public final long getThreadId()
  {
    Thread thread = _thread;

    if (thread != null)
      return thread.getId();
    else
      return -1;
  }

  //
  // request information
  //

  /**
   * Returns the time the request started
   */
  public final long getRequestStartTime()
  {
    return _requestStartTime;
  }

  /**
   * Returns the time the current request has taken.
   */
  public final long getRequestActiveTime()
  {
    if (_requestStartTime > 0)
      return Alarm.getCurrentTime() - _requestStartTime;
    else
      return -1;
  }

  /**
   * Returns the idle expire time (keepalive or suspend).
   */
  public long getIdleExpireTime()
  {
    return _idleExpireTime;
  }

  /**
   * Returns the idle start time (keepalive or suspend)
   */
  public long getIdleStartTime()
  {
    return _idleStartTime;
  }

  /**
   * Returns the current keepalive task
   */
  public Runnable getAcceptTask()
  {
    return _acceptTask;
  }

  /**
   * Returns the current keepalive task (request or duplex)
   */
  public Runnable getKeepaliveTask()
  {
    return _keepaliveTask;
  }

  /**
   * Returns the current write task
   */
  public Runnable getResumeTask()
  {
    return _resumeTask;
  }

  //
  // statistics state
  //

  /**
   * Returns the user statistics state
   */
  public String getDisplayState()
  {
    return _displayState;
  }

  /**
   * Sets the user statistics state
   */
  public void setStatState(String state)
  {
    _displayState = state;
  }

  //
  // async/comet predicates
  //

  public ConnectionController getController()
  {
    return _controller;
  }

  public WebApp getAsyncDispatchWebApp()
  {
    return null;
  }

  public String getAsyncDispatchUrl()
  {
    if (_controller != null)
      return _controller.getForwardPath();
    else
      return null;
  }

  /**
   * Poll the socket to test for an end-of-file.
   */
  public boolean isReadEof()
  {
    QSocket socket = _socket;

    if (socket == null)
      return true;

    try {
      StreamImpl s = socket.getStream();

      int len = s.read(_testBuffer, 0, 0);

      return len < 0;
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);

      return true;
    }
  }

  //
  // request processing
  //

  /**
   * Handles a new connection/socket from the client.
   */
  RequestState handleRequests()
    throws IOException
  {
    Thread thread = Thread.currentThread();

    boolean isValid = false;
    RequestState result = null;

   try {
     // clear the interrupted flag
     Thread.interrupted();

     _currentRequest.set(_request);

     result = handleRequestsImpl();
   } catch (ClientDisconnectException e) {
     _port.addLifetimeClientDisconnectCount();

     if (log.isLoggable(Level.FINER)) {
       log.finer(dbgId() + e);
     }
   } catch (InterruptedIOException e) {
     if (log.isLoggable(Level.FINEST)) {
       log.log(Level.FINEST, dbgId() + e, e);
     }
   } catch (IOException e) {
     if (log.isLoggable(Level.FINE)) {
       log.log(Level.FINE, dbgId() + e, e);
     }
   } finally {
     thread.setContextClassLoader(_systemClassLoader);

     _currentRequest.set(null);

     if (result == null)
       destroy();
   }

   if (result == RequestState.DUPLEX)
     return _keepaliveTask.doTask();
   else
     return result;
  }

  /**
   * Handles a new connection/socket from the client.
   */
  private RequestState handleRequestsImpl()
    throws IOException
  {
    Thread thread = Thread.currentThread();
    ClassLoader systemLoader = _systemClassLoader;

    RequestState result = null;

    do {
      thread.setContextClassLoader(systemLoader);

      if (_port.isClosed()) {
        return RequestState.EXIT;
      }

      boolean isStatKeepalive = _state.isKeepalive();
      _state = _state.toActive();
      _isWake = false;

      if (_controller != null)
        throw new IllegalStateException(L.l("can't shift to active from duplex {0} controller {1}",
                                            _state, _controller));

      if ((result = processKeepalive()) != RequestState.REQUEST) {
        return result;
      }

      if (! getRequest().handleRequest()) {
        _isKeepalive = false;
      }

      // statistics
      gatherStatistics(isStatKeepalive);

      if (_state == ConnectionState.DUPLEX) {
        // duplex (xmpp/hmtp) handling
        return RequestState.DUPLEX;
      }
      else if (_state == ConnectionState.COMET) {
        _port.suspend(this);

        return RequestState.THREAD_DETACHED;
      }
    } while (_isKeepalive
             && (result = processKeepalive()) == RequestState.REQUEST);

    return result;
  }

  private void gatherStatistics(boolean isStatKeepalive)
  {
    if (_requestStartTime > 0) {
      long startTime = _requestStartTime;
      _requestStartTime = 0;

      if (isStatKeepalive)
        _port.addLifetimeKeepaliveCount();

      _port.addLifetimeRequestCount();

      long now = Alarm.getCurrentTime();
      _port.addLifetimeRequestTime(now - startTime);

      ReadStream rs = getReadStream();
      long readCount = rs.getPosition();
      rs.clearPosition();
      _port.addLifetimeReadBytes(readCount);

      WriteStream ws = getWriteStream();
      long writeCount = ws.getPosition();
      ws.clearPosition();
      _port.addLifetimeWriteBytes(writeCount);
    }
  }

  /**
   * Starts a keepalive, either returning available data or
   * returning false to close the loop
   *
   * If keepaliveRead() returns true, data is available.
   * If it returns false, either the connection is closed,
   * or the connection has been registered with the select.
   */
  private RequestState processKeepalive()
    throws IOException
  {
    Port port = _port;

    // quick timed read to see if data is already available
    if (port.keepaliveThreadRead(getReadStream())) {
      return RequestState.REQUEST;
    }

    _idleStartTime = Alarm.getCurrentTime();
    _idleExpireTime = _idleStartTime + _idleTimeout;

    // start keepalive, checking that Port allows another keepalive
    if (! _port.keepaliveBegin(this, _connectionStartTime)) {
      close();

      return RequestState.EXIT;
    }

    _state = _state.toKeepaliveSelect();

    // use select manager if available
    if (_port.getSelectManager() != null) {
      // keepalive to select manager succeeds
      if (_port.getSelectManager().keepalive(this)) {
        if (log.isLoggable(Level.FINE))
          log.fine(dbgId() + " keepalive (select)");

        return RequestState.THREAD_DETACHED;
      }
      // keepalive to select manager fails (e.g. filled select manager)
      else {
        log.warning(dbgId() + " failed keepalive (select)");

        _state = _state.toActive();
        port.keepaliveEnd(this);

        close();

        return RequestState.EXIT;
      }
    }
    else {
      if (log.isLoggable(Level.FINE))
        log.fine(dbgId() + " keepalive (thread)");

      // if blocking read has available data
      if (getReadStream().waitForRead()) {
        port.keepaliveEnd(this);

        _state = _state.toActive();

        return RequestState.REQUEST;
      }
      // blocking read timed out or closed
      else {
        _state = _state.toActive();
        port.keepaliveEnd(this);

        close();

        return RequestState.EXIT;
      }
    }
  }

  //
  // state transitions
  //

  /**
   * Sets the keepalive state.  Called only by the SelectManager and Port.
   */
  public void clearKeepalive()
  {
    if (! _isKeepalive)
      log.warning(this + " illegal state: clearing keepalive with inactive keepalive");

    _isKeepalive = false;
  }

  /**
   * Begins an active connection.
   */
  public final long beginActive()
  {
    _state = _state.toActive();
    _requestStartTime = Alarm.getCurrentTime();
    return _requestStartTime;
  }

  /**
   * Ends an active connection.
   */
  public final void endActive()
  {
    // state change?
    // _requestStartTime = 0;
  }

  /**
   * Finish a request.
   */
  public void finishRequest()
  {
    ConnectionController controller = _controller;
    _controller = null;

    if (controller != null)
      controller.closeImpl();

    // XXX: to finishRequest
    _state = _state.toCompleteComet();
  }

  @Override
  public void killKeepalive()
  {
    _isKeepalive = false;
  }

  @Override
  public boolean toKeepalive()
  {
    if (! _isKeepalive)
      return false;

    _isKeepalive = _port.allowKeepalive(_connectionStartTime);

    return _isKeepalive;
  }

  /**
   * Closes on shutdown.
   */
  public void closeOnShutdown()
  {
    QSocket socket = _socket;

    if (socket != null) {
      try {
        socket.close();
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
      }

      // Thread.yield();
    }
  }

  //
  // async/comet state transitions

  /**
   * Starts a comet request
   */
  @Override
  public ConnectionCometController toComet(boolean isTop,
                                           ServletRequest request,
                                           ServletResponse response)
  {
    if (_controller != null)
      throw new IllegalStateException(L.l("comet mode can't start in state '{0}'",
                                          _state));

    ConnectionCometController controller
      = super.toComet(isTop, request, response);

    _controller = controller;

    if (log.isLoggable(Level.FINER))
      log.finer(this + " starting comet");

    _state = ConnectionState.COMET;

    return controller;
  }

  void toResume()
  {
    _isWake = false;
    _idleStartTime = 0;
  }

  void toWake()
  {
    _isWake = true;
  }

  public void suspend()
  {
    if (_controller != null)
      _controller.suspend();
  }

  void toSuspend()
  {
    _idleStartTime = Alarm.getCurrentTime();
    _idleExpireTime = _idleStartTime + _idleTimeout;
  }

  /**
   * Wakes the connection (comet-style).
   */
  protected boolean wake()
  {
    if (! _state.isComet())
      return false;

    _isWake = true;

    // comet
    if (getPort().resume(this)) {
      log.fine(dbgId() + " wake");
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Starts a full duplex (tcp style) request for hmtp/xmpp
   */
  public TcpDuplexController startDuplex(TcpDuplexHandler handler)
  {
    if (_controller != null) {
      ConnectionState state = _state;

      log.warning(this + " toDuplex call failed for state " + state);

      destroy();

      throw new IllegalStateException(L.l("duplex mode can't start in state '{0}'",
                                          state));
    }

    TcpDuplexController duplex = new TcpDuplexController(this, handler);

    _controller = duplex;

    if (log.isLoggable(Level.FINER))
      log.finer(this + " starting duplex");

    _state = ConnectionState.DUPLEX;
    _isKeepalive = false;
    _keepaliveTask = new DuplexReadTask(duplex);

    return duplex;
  }

  public void closeController(ConnectionCometController controller)
  {
    if (controller == _controller) {
      _controller = null;

      closeControllerImpl();
    }
  }

  /**
   * Closes the controller.
   */
  protected void closeControllerImpl()
  {
    _state = _state.toCompleteComet();

    getPort().resume(this);
  }

  public void close()
  {
    closeImpl();
  }

  /**
   * Closes the connection.
   */
  private void closeImpl()
  {
    ConnectionState state;

    synchronized (this) {
      state = _state;

      if (state == ConnectionState.IDLE || state.isClosed())
        return;

      _state = _state.toClosed();
    }

    QSocket socket = _socket;

    // detach any comet
    getPort().detach(this);

    getRequest().protocolCloseEvent();

    ConnectionController controller = _controller;
    _controller = null;

    if (controller != null)
      controller.closeImpl();

    _isKeepalive = false;

    Port port = getPort();

    if (state.isKeepalive()) {
      port.keepaliveEnd(this);
    }

    if (log.isLoggable(Level.FINER)) {
      if (port != null)
        log.finer(dbgId() + " closing connection " + this + ", total=" + port.getConnectionCount());
      else
        log.finer(dbgId() + " closing connection " + this);
    }

    try {
      getWriteStream().close();
    } catch (Throwable e) {
      log.log(Level.FINE, e.toString(), e);
    }

    try {
      getReadStream().close();
    } catch (Throwable e) {
      log.log(Level.FINE, e.toString(), e);
    }

    if (socket != null) {
      getPort().closeSocket(socket);

      try {
        socket.close();
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
  }

  /**
   * Destroys the connection()
   */
  public final void destroy()
  {
    _socket.forceShutdown();

    closeImpl();

    ConnectionState state = _state;
    _state = ConnectionState.DESTROYED;

    if (state != ConnectionState.DESTROYED) {
      getPort().kill(this);
    }
  }

  /**
   * Completion processing at the end of the thread
   */
  void finishThread()
  {
    closeImpl();

    if (_state != ConnectionState.IDLE
        && _state != ConnectionState.DESTROYED) {
      if (_controller != null) {
        throw new IllegalStateException(L.l("{0} can't switch to idle from {1} with an active controller {2}",
                                            this, _state, _controller));
      }

      _state = _state.toIdle();

      getPort().free(this);
    }
  }

  protected String dbgId()
  {
    if (_dbgId == null) {
      Object serverId = Environment.getAttribute("caucho.server-id");

      if (serverId != null)
        _dbgId = (getClass().getSimpleName() + "[id=" + getId()
                  + "," + serverId + "] ");
      else
        _dbgId = (getClass().getSimpleName() + "[id=" + getId() + "]");
    }

    return _dbgId;
  }

  @Override
  public String toString()
  {
    return "TcpConnection[id=" + _id + "," + _port.toURL() + "," + _state + "]";
  }

  enum RequestState {
    REQUEST,
    THREAD_DETACHED,
    DUPLEX,
    EXIT
  };

  abstract class ConnectionReadTask implements Runnable {
    /**
     * Handles the read request for the connection
     */
    abstract RequestState doTask()
      throws IOException;

    public void run()
    {
      runThread();
    }

    public void runThread()
    {
      Thread thread = Thread.currentThread();
      String oldThreadName = thread.getName();

      thread.setName(_id);
      _thread = thread;

      ClassLoader systemLoader = _systemClassLoader;
      thread.setContextClassLoader(systemLoader);

      RequestState result = null;

      _port.threadBegin(TcpConnection.this);

      try {
        result = doTask();
      } catch (OutOfMemoryError e) {
        CauchoSystem.exitOom(getClass(), e);
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
      } finally {
        thread.setName(oldThreadName);
        _port.threadEnd(TcpConnection.this);

        if (result == null)
          destroy();

        if (result != RequestState.THREAD_DETACHED)
          finishThread();
      }
    }

    @Override
    public String toString()
    {
      return getClass().getSimpleName() + "[" + TcpConnection.this + "]";
    }
  }

  class AcceptTask extends ConnectionReadTask {
    public void run()
    {
      Port port = _port;

      port.startConnection(TcpConnection.this);

      runThread();
    }

    /**
     * Loop to accept new connections.
     */
    @Override
    RequestState doTask()
      throws IOException
    {
      Port port = _port;

      // next state is keepalive
      _keepaliveTask = _keepaliveRequestTask;

      ServerRequest request = getRequest();

      RequestState result = RequestState.EXIT;

      // _state = _state.toAccept();

      while (! _port.isClosed() && _state != ConnectionState.DESTROYED) {
        if (_controller != null) {
          throw new IllegalStateException(L.l("{0} cannot change from {1} to accept with an active controller {2}", this, _state, _controller));
        }

        _state = _state.toAccept();
        setStatState("accept");

        if (! _port.accept(_socket)) {
          setStatState(null);
          close();

          return RequestState.EXIT;
        }

        _connectionStartTime = Alarm.getCurrentTime();

        setStatState("read");
        initSocket();

        _request.startConnection();
        _isKeepalive = true;

        result = handleRequests();

        if (result == RequestState.THREAD_DETACHED) {
          return result;
        }
        else if (result == RequestState.DUPLEX) {
          setStatState("duplex");
          
          return _keepaliveTask.doTask();
        }
        
        setStatState(null);

        close();
      }

      return RequestState.EXIT;
    }

    /**
     * Initialize the socket for a new connection
     */
    private void initSocket()
      throws IOException
    {
      _idleTimeout = _port.getKeepaliveTimeout();

      getWriteStream().init(_socket.getStream());
      getReadStream().init(_socket.getStream(), getWriteStream());

      if (log.isLoggable(Level.FINE)) {
        log.fine(dbgId() + "starting connection " + TcpConnection.this + ", total=" + _port.getConnectionCount());
      }
    }
  }

  class KeepaliveRequestTask extends ConnectionReadTask {
    public void run()
    {
      Port port = _port;

      port.keepaliveEnd(TcpConnection.this);

      runThread();
    }

    @Override
    public RequestState doTask()
      throws IOException
    {
      _state = _state.toActive();

      RequestState result = handleRequests();

      if (result == RequestState.THREAD_DETACHED) {
        return result;
      }
      else if (result == RequestState.DUPLEX) {
        return _keepaliveTask.doTask();
      }

      // acceptTask significantly faster than finishing
      return _acceptTask.doTask();
    }
  }

  class DuplexReadTask extends ConnectionReadTask {
    private final TcpDuplexController _duplex;

    DuplexReadTask(TcpDuplexController duplex)
    {
      _duplex = duplex;
    }

    public void run()
    {
      Port port = _port;

      port.keepaliveEnd(TcpConnection.this);

      runThread();
    }

    @Override
    public RequestState doTask()
      throws IOException
    {
      _state = _state.toActive();

      RequestState result;
      long position = 0;
      int retry = 256;

      ReadStream readStream = getReadStream();

      while ((result = processKeepalive()) == RequestState.REQUEST) {
        _duplex.serviceRead();

        if (position != readStream.getPosition()) {
          retry = 256;
          position = readStream.getPosition();
        }
        else if (retry-- < 0) {
          log.warning(_duplex + " was not processing any data. Shutting down.");
          close();
          return RequestState.EXIT;
        }
      }

      return result;
    }
  }

  class ResumeTask implements Runnable {
    public void run()
    {
      boolean isValid = false;

      try {
        ConnectionCometController comet
          = (ConnectionCometController) _controller;

        if (comet != null)
          comet.startResume();

        _isWake = false;

        if (getRequest().handleResume()) {
          // XXX:keepalive?
          _port.suspend(TcpConnection.this);

          isValid = true;
        }
        else if (_isKeepalive) {
          isValid = true;
          _keepaliveTask.run();
        }
      } catch (IOException e) {
        log.log(Level.FINE, e.toString(), e);
      } catch (OutOfMemoryError e) {
        CauchoSystem.exitOom(getClass(), e);
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
      } finally {
        if (! isValid) {
          finishThread();
        }
      }
    }
  }

  class Admin extends AbstractManagedObject implements TcpConnectionMXBean {
    Admin()
    {
      super(_systemClassLoader);
    }

    public String getName()
    {
      return _name;
    }

    public long getThreadId()
    {
      return TcpConnection.this.getThreadId();
    }

    public long getRequestActiveTime()
    {
      return TcpConnection.this.getRequestActiveTime();
    }

    public String getUrl()
    {
      ServerRequest request = TcpConnection.this.getRequest();

      if (request instanceof AbstractHttpRequest) {
        AbstractHttpRequest req = (AbstractHttpRequest) request;

        if (! "".equals(req.getRequestURI())) {
          String url = String.valueOf(req.getRequestURL());

          return url;
        }
        else {
          Port port = TcpConnection.this.getPort();

          if (port.getAddress() == null)
            return "accept://*:" + port.getPort();
          else
            return "accept://" + port.getAddress() + ":" + port.getPort();
        }
      }


      return null;
    }

    public String getState()
    {
      return TcpConnection.this.getState();
    }

    public String getDisplayState()
    {
      return TcpConnection.this.getDisplayState();
    }

    void register()
    {
      registerSelf();
    }

    void unregister()
    {
      unregisterSelf();
    }
  }

  static {
    _systemClassLoader = ClassLoader.getSystemClassLoader();
  }
}
