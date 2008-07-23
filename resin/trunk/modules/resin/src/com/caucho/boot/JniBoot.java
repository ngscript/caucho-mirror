/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.boot;

import java.util.ArrayList;
import java.util.HashMap;

import com.caucho.vfs.*;

/**
 * Resin's bootstrap class.
 */
public class JniBoot implements Boot {
  private JniProcessAPI _jniProcess;
  
  public JniBoot()
  {
    _jniProcess = new JniProcess();
    
    /*
    
    JniLoader loader = new JniLoader(resinHome);

    try {
      Class cl = Class.forName("com.caucho.boot.JniProcess", false, loader);
      
      System.out.println("CL: " + cl);
      
      _jniProcess = (JniProcessAPI) cl.newInstance();

      System.out.println("JP: " + _jniProcess);
    } catch (RuntimeException e) {
      e.printStackTrace();
      throw e;
    } catch (Error e) {
      e.printStackTrace();
      throw e;
    } catch (Exception e) {
      e.printStackTrace();
      
      throw new RuntimeException(e);
    }
    */
  }

  public boolean isValid()
  {
    return _jniProcess != null && _jniProcess.isValid();
  }
  
  public void clearSaveOnExec()
  {
    if (_jniProcess != null)
      _jniProcess.clearSaveOnExec();
  }
  
  public Process exec(ArrayList<String> argv,
		      HashMap<String,String> env,
		      String chroot,
		      String pwd,
		      String user,
		      String group)
  {
    if (_jniProcess != null)
      return _jniProcess.create(argv, env, chroot, pwd, user, group);
    else
      return null;
  }
}
