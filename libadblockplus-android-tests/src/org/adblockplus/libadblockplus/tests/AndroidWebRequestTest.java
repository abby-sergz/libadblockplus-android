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

import org.adblockplus.libadblockplus.ManagedSchedulerImpl;
import org.adblockplus.libadblockplus.MockTimer;
import org.adblockplus.libadblockplus.WebRequest;
import org.adblockplus.libadblockplus.android.AndroidWebRequest;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.Timer;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.android.Utils;


import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;

public class AndroidWebRequestTest extends BaseJsTest
{
  MockTimer timer;

  private ManagedSchedulerImpl webRequestScheduler;
  @Override
  protected WebRequest createWebRequest()
  {
    webRequestScheduler = new ManagedSchedulerImpl();
    return new AndroidWebRequest(webRequestScheduler, true, true);
  }

  @Override
  public Timer createTimer()
  {
    return timer = new MockTimer();
  }

  private void waitForVariable(String getVariable)
  {
    while (jsEngine.evaluate(getVariable).isUndefined() && webRequestScheduler.processNextTask())
    {
    }
  }

  @Test
  public void testRealWebRequest()
  {
    // This URL should redirect to easylist-downloads.adblockplus.org and we
    // should get the actual filter list back.
    jsEngine.evaluate(
      "_webRequest.GET('https://easylist-downloads.adblockplus.org/easylist.txt', {}, " +
      "function(result) {foo = result;} )");
    waitForVariable("this.foo");

    String response = jsEngine.evaluate("foo.responseText").asString();
    assertNotNull(response);
    assertEquals(
      ServerResponse.NsStatus.OK.getStatusCode(),
      jsEngine.evaluate("foo.status").asLong());
    assertEquals(200l, jsEngine.evaluate("foo.responseStatus").asLong());
    assertEquals(
      "[Adblock Plus ",
      jsEngine.evaluate("foo.responseText.substr(0, 14)").asString());
    JsValue jsHeaders = jsEngine.evaluate("foo.responseHeaders");
    assertNotNull(jsHeaders);
    assertFalse(jsHeaders.isUndefined());
    assertFalse(jsHeaders.isNull());
    assertTrue(jsHeaders.isObject());
    assertEquals(
      "text/plain",
      jsEngine.evaluate("foo.responseHeaders['content-type'].substr(0, 10)").asString());
    assertTrue(jsEngine.evaluate("foo.responseHeaders['location']").isUndefined());
  }

  @Test
  public void testXMLHttpRequest() throws InterruptedException
  {
    // creating not used anywhere FilterEngine object is not as useless as it seems:
    // it loads compat.js JsEngine to add XMLHttpRequest class support
    FilterEngine filterEngine = null;
    try {
      filterEngine = Utils.createFilterEngine(jsEngine);

      jsEngine.evaluate(
          "var result;\n" +
              "var request = new XMLHttpRequest();\n" +
              "request.open('GET', 'https://easylist-downloads.adblockplus.org/easylist.txt');\n" +
              "request.setRequestHeader('X', 'Y');\n" +
              "request.setRequestHeader('X2', 'Y2');\n" +
              "request.overrideMimeType('text/plain');\n" +
              "request.addEventListener('load',function() {result=request.responseText;}, false);\n" +
              "request.addEventListener('error',function() {result='error';}, false);\n" +
              "request.send(null);");
      waitForVariable("result");

      assertEquals(
          ServerResponse.NsStatus.OK.getStatusCode(),
          jsEngine.evaluate("request.channel.status").asLong());

      assertEquals(200l, jsEngine.evaluate("request.status").asLong());
      assertEquals("[Adblock Plus ", jsEngine.evaluate("result.substr(0, 14)").asString());
      assertEquals(
          "text/plain",
          jsEngine.evaluate("request.getResponseHeader('Content-Type').substr(0, 10)").asString());
      assertTrue(jsEngine.evaluate("request.getResponseHeader('Location')").isNull());
    }
    finally
    {
      if (filterEngine != null)
      {
        filterEngine.dispose();
      }
    }
  }

  @Test
  public void testGetElemhideElements() throws MalformedURLException, InterruptedException
  {
    FilterEngine filterEngine = null;
    try {
      filterEngine = Utils.createFilterEngine(jsEngine);
      // libadblockplus is using timer with zero timeout in order to implement Utils.runAsync
      // what is used by adblockpluscore to schedule subscription updates.
      timer.processImmediateTimers();
      // now process all pending web requests
      while(webRequestScheduler.processNextTask());

      final String url = "www.mobile01.com/somepage.html";

      boolean isDocumentWhitelisted = filterEngine.isDocumentWhitelisted(url, null);
      assertFalse(isDocumentWhitelisted);

      boolean isElemhideWhitelisted = filterEngine.isElemhideWhitelisted(url, null);
      assertFalse(isElemhideWhitelisted);

      List<String> selectors = filterEngine.getElementHidingSelectors(url);
      assertNotNull(selectors);
      assertTrue(selectors.size() > 0);
    }
    finally
    {
      if (filterEngine != null)
      {
        filterEngine.dispose();
      }
    }
  }
}
