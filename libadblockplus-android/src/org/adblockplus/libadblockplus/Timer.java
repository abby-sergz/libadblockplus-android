/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-2017 eyeo GmbH
 *
 * Adblock Plus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * Adblock Plus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Adblock Plus.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.adblockplus.libadblockplus;

public interface Timer
{
  interface Callback extends Disposable
  {
    void call();
  }
  /**
   * Callback should be disposed as soon as possible after invocation of Callback.call.
   */
  void setTimer(long timeoutMsec, Callback callback);
}

final class TimerCallbackImpl implements Timer.Callback
{
  static
  {
    System.loadLibrary("adblockplus-jni");
    registerNatives();
  }

  private final long ptr;
  private final Disposer disposer;

  TimerCallbackImpl(long ptr)
  {
    this.ptr = ptr;
    this.disposer = new Disposer(this, new DisposeWrapper(this.ptr));
  }

  /**
   * It should be called only once.
   */
  @Override
  public void call()
  {
    TimerCallbackCall(this.ptr);
  }

  @Override
  public void dispose()
  {
    this.disposer.dispose();
  }

  private final class DisposeWrapper implements Disposable
  {
    public final long ptr;
    public DisposeWrapper(long ptr)
    {
      this.ptr = ptr;
    }

    @Override
    public void dispose()
    {
      TimerCallbackDtor(this.ptr);
    }
  }

  private final static native void registerNatives();
  private final static native void TimerCallbackDtor(long ptr);
  private final static native void TimerCallbackCall(long ptr);
}