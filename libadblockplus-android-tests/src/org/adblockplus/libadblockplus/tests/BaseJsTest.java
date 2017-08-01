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

import org.adblockplus.libadblockplus.AppInfo;
import org.adblockplus.libadblockplus.JsEngine;
import org.adblockplus.libadblockplus.LazyLogSystem;
import org.adblockplus.libadblockplus.LogSystem;
import org.adblockplus.libadblockplus.ThrowingWebRequest;
import org.adblockplus.libadblockplus.WebRequest;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.test.PerformanceTestCase;

public abstract class BaseJsTest extends InstrumentationTestCase
{
  protected JsEngine jsEngine;

  @Override
  protected void setUp() throws Exception
  {
    super.setUp();

    jsEngine = new JsEngine(AppInfo.builder().build(), createWebRequest(),
        createLogSystem());
    jsEngine.setDefaultFileSystem(getContext().getFilesDir().getAbsolutePath());
  }

  protected LogSystem createLogSystem()
  {
    return null;
  }

  protected WebRequest createWebRequest()
  {
    return new ThrowingWebRequest();
  }

  protected Context getContext()
  {
    return getInstrumentation().getTargetContext();
  }
}
