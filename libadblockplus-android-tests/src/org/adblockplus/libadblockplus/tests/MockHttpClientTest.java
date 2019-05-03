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

package org.adblockplus.libadblockplus.tests;

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.HttpRequest;
import org.adblockplus.libadblockplus.ServerResponse;

import org.adblockplus.libadblockplus.android.Utils;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.Arrays;

public class MockHttpClientTest extends BaseJsTest
{

  private class LocalMockWebRequest extends HttpClient
  {
    @Override
    public ServerResponse request(HttpRequest request)
    {
      try
      {
        Thread.sleep(50);
      }
      catch (InterruptedException e)
      {
        throw new RuntimeException(e);
      }

      ServerResponse result = new ServerResponse();
      result.setStatus(ServerResponse.NsStatus.OK);
      result.setResponseStatus(123);
      result.setResponseHeaders(Arrays.asList(new HeaderEntry("Foo", "Bar")));

      result.setResponse(Utils.stringToByteBuffer(
        request.getUrl() + "\n" +
        request.getHeaders().get(0).getKey() + "\n" +
        request.getHeaders().get(0).getValue(),
        Charset.forName("UTF-8")));
      return result;
    }
  }

  @Override
  protected void setUp() throws Exception
  {
    super.setUp();

    jsEngine.setHttpClient(new LocalMockWebRequest());
  }

  @Test
  public void testBadCall()
  {
    try
    {
      jsEngine.evaluate("_webRequest.GET()");
      fail();
    }
    catch (AdblockPlusException e)
    {
      // ignored
    }

    try
    {
      jsEngine.evaluate("_webRequest.GET('', {}, function(){})");
      fail();
    }
    catch (AdblockPlusException e)
    {
      // ignored
    }

    try
    {
      jsEngine.evaluate("_webRequest.GET({toString: false}, {}, function(){})");
      fail();
    }
    catch (AdblockPlusException e)
    {
      // ignored
    }

    try
    {
      jsEngine.evaluate("_webRequest.GET('http://example.com/', null, function(){})");
      fail();
    }
    catch (AdblockPlusException e)
    {
      // ignored
    }

    try
    {
      jsEngine.evaluate("_webRequest.GET('http://example.com/', {}, null)");
      fail();
    }
    catch (AdblockPlusException e)
    {
      // ignored
    }

    try
    {
      jsEngine.evaluate("_webRequest.GET('http://example.com/', {}, function(){}, 0)");
      fail();
    }
    catch (AdblockPlusException e)
    {
      // ignored
    }
  }

  @Test
  public void testSuccessfulRequest() throws InterruptedException
  {
    jsEngine.evaluate(
      "_webRequest.GET('http://example.com/', {X: 'Y'}, function(result) {foo = result;} )");
    assertTrue(jsEngine.evaluate("this.foo").isUndefined());

    Thread.sleep(200);

    assertEquals(
      ServerResponse.NsStatus.OK.getStatusCode(),
      jsEngine.evaluate("foo.status").asLong());
    assertEquals("http://example.com/\nX\nY", jsEngine.evaluate("foo.responseText").asString());
    assertEquals("{\"Foo\":\"Bar\"}",
      jsEngine.evaluate("JSON.stringify(foo.responseHeaders)").asString());
  }

}
