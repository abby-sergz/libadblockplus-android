/*
 * This file is part of Adblock Plus <https://adblockplus.org/>,
 * Copyright (C) 2006-present eyeo GmbH
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

public abstract class HttpClient implements Disposable
{
  static
  {
    System.loadLibrary("adblockplus-jni");
    registerNatives();
  }

  /**
   * Possible values for request method argument (see `request(..)` method)
   */
  public static String REQUEST_METHOD_GET = "GET";
  public static String REQUEST_METHOD_POST = "POST";
  public static String REQUEST_METHOD_HEAD = "HEAD";
  public static String REQUEST_METHOD_OPTIONS = "OPTIONS";
  public static String REQUEST_METHOD_PUT = "PUT";
  public static String REQUEST_METHOD_DELETE = "DELETE";
  public static String REQUEST_METHOD_TRACE = "TRACE";

  /**
   * Checks if HTTP status code is a redirection.
   * @param httpStatusCode HTTP status code to check.
   * @return True for redirect status code.
   */
  public static boolean isRedirectCode(int httpStatusCode)
  {
    return httpStatusCode >= 300 && httpStatusCode <= 399;
  }

  /**
   * Checks if HTTP status code is a success code.
   * @param httpStatusCode HTTP status code to check.
   * @return True for success status code.
   */
  public static boolean isSuccessCode(int httpStatusCode)
  {
    return httpStatusCode >= 200 && httpStatusCode <= 299;
  }

  public HttpClient()
  {
    this.ptr = ctor(this);
    this.disposer = new Disposer(this, new DisposeWrapper(this.ptr));
  }

  private final Disposer disposer;
  protected final long ptr;

  /**
   * Performs a HTTP request.
   * @param request HttpRequest
   * @return server response
   */
  public abstract ServerResponse request(final HttpRequest request);

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
      dtor(this.ptr);
    }
  }

  private final static native void registerNatives();

  private final static native long ctor(Object callbackObject);

  private final static native void dtor(long ptr);
}
