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

import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.WebRequest;
import org.adblockplus.libadblockplus.android.Utils;

import java.util.LinkedList;
import java.util.List;

public abstract class FilterEngineGenericTest extends BaseJsTest
{
  protected FilterEngine filterEngine;

  protected final static class WebRequestTask
  {
    public final String url;
    public final List<HeaderEntry> headers;
    public final WebRequest.GetCallback getCallback;
    public WebRequestTask(String url, List<HeaderEntry> headers, WebRequest.GetCallback getCallback)
    {
      this.url = url;
      this.headers = headers;
      this.getCallback = getCallback;
    }
  }

  protected class ManagedWebRequest extends WebRequest
  {
    @Override
    public void httpGET(String url, List<HeaderEntry> headers, GetCallback getCallback)
    {
      webRequestTasks.add(new WebRequestTask(url, headers, getCallback));
    }
  }

  List<WebRequestTask> webRequestTasks = new LinkedList<WebRequestTask>();

  @Override
  protected void setUp() throws Exception
  {
    super.setUp();
    filterEngine = Utils.createFilterEngine(jsEngine);
  }

  @Override
  protected void tearDown()
  {
    if (filterEngine != null)
    {
      filterEngine.dispose();
    }
  }

  @Override
  protected WebRequest createWebRequest()
  {
    return new ManagedWebRequest();
  }
}
