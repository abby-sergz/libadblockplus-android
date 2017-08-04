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

package org.adblockplus.libadblockplus.tests;

import android.os.SystemClock;

import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.IsAllowedConnectionCallback;
import org.adblockplus.libadblockplus.MockTimer;
import org.adblockplus.libadblockplus.Timer;
import org.adblockplus.libadblockplus.Subscription;
import org.adblockplus.libadblockplus.WebRequest;
import org.adblockplus.libadblockplus.android.AndroidWebRequest;
import org.adblockplus.libadblockplus.android.Utils;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

public class IsAllowedConnectionCallbackTest extends BaseJsTest
{
  private MockTimer timer;

  private static final class TestRequest extends WebRequest
  {
    private List<String> urls = new LinkedList<String>();

    public List<String> getUrls()
    {
      return urls;
    }

    @Override
    public void httpGET(String url, List<HeaderEntry> headers, GetCallback getCallback)
    {
      urls.add(url);
      getCallback.dispose();
    }
  }

  private static final class TestCallback extends IsAllowedConnectionCallback
  {
    private boolean result;
    private boolean invoked;
    private String connectionType;

    public boolean isResult()
    {
      return result;
    }

    public void setResult(boolean result)
    {
      this.result = result;
    }

    public String getConnectionType()
    {
      return connectionType;
    }

    public boolean isInvoked()
    {
      return invoked;
    }

    @Override
    public boolean isConnectionAllowed(String connectionType)
    {
      this.invoked = true;
      this.connectionType = connectionType;

      return result;
    }
  }

  private TestRequest request;
  private TestCallback callback;
  private FilterEngine filterEngine;

  @Override
  protected void setUp() throws Exception
  {
    request = new TestRequest();
    super.setUp();

    callback = new TestCallback();
    filterEngine = Utils.createFilterEngine(jsEngine, callback);
  }

  @Override
  public void tearDown() throws Exception
  {
    if (filterEngine != null)
    {
      filterEngine.dispose();
      filterEngine = null;
    }
    super.tearDown();
  }

  @Override
  protected Timer createTimer()
  {
    return timer = new MockTimer();
  }

  @Override
  protected WebRequest createWebRequest()
  {
    return request;
  }

  private void updateSubscriptions()
  {
    for (final Subscription s : this.filterEngine.getListedSubscriptions())
    {
      try
      {
        s.updateFilters();
      }
      finally
      {
        s.dispose();
      }
    }
    // libadblockplus is using timer with zero timeout in order to implement Utils.runAsync
    // what is used by adblockpluscore to schedule subscription updates.
    timer.processImmediateTimers();
  }

  @Test
  public void testAllow()
  {
    final String allowedConnectionType = "wifi1";
    filterEngine.setAllowedConnectionType(allowedConnectionType);
    callback.setResult(true);

    assertEquals(0, request.getUrls().size());
    assertFalse(callback.isInvoked());

    updateSubscriptions();

    assertTrue(callback.isInvoked());
    assertNotNull(callback.getConnectionType());
    assertEquals(allowedConnectionType, callback.getConnectionType());

    assertTrue(request.getUrls().size() > 0);
  }

  @Test
  public void testDeny()
  {
    final String allowedConnectionType = "wifi2";
    filterEngine.setAllowedConnectionType(allowedConnectionType);

    callback.setResult(false);
    assertEquals(0, request.getUrls().size());

    updateSubscriptions();

    assertTrue(callback.isInvoked());
    assertNotNull(callback.getConnectionType());
    assertEquals(allowedConnectionType, callback.getConnectionType());

    assertEquals(0, request.getUrls().size());
  }
}
