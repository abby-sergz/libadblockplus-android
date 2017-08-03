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

import java.util.List;

public abstract class WebRequest
{
  static
  {
    System.loadLibrary("adblockplus-jni");
    registerNatives();
  }

  public final static class GetCallback implements Disposable
  {
    protected final long ptr;
    private final Disposer disposer;

    public GetCallback(long ptr)
    {
      this.ptr = ptr;
      this.disposer = new Disposer(this, new DisposeWrapper(this.ptr));
    }

    public void call(ServerResponse serverResponse)
    {
      GetCallbackCall(this.ptr, serverResponse);
    }

    @Override
    public void dispose()
    {
      this.disposer.dispose();
    }

    private final static class DisposeWrapper implements Disposable
    {
      private final long ptr;

      public DisposeWrapper(final long ptr)
      {
        this.ptr = ptr;
      }

      @Override
      public void dispose()
      {
        GetCallbackDtor(this.ptr);
      }
    }
  }

  public abstract void httpGET(String url, List<HeaderEntry> headers, GetCallback getCallback);

  private final static native void registerNatives();
  private final static native void GetCallbackCall(long ptr, Object serverResponse);
  private final static native void GetCallbackDtor(long ptr);
}
