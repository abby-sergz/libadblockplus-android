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

import org.adblockplus.libadblockplus.android.settings.BaseSettingsFragment;
import org.junit.Test;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.AbstractMap.SimpleEntry;
import org.adblockplus.libadblockplus.Timer;

public final class MockTimerTest extends BaseJsTest
{
  private MockTimer timer;

  private final class MockTimer implements Timer
  {
    public List<Entry<Long, Callback>> timers = new LinkedList<Entry<Long, Callback>>();

    @Override
    public void setTimer(long timeoutMsec, Callback callback)
    {
      timers.add(new SimpleEntry<Long, Callback>(timeoutMsec, callback));
    }

    public void processTimer()
    {
      Callback callback = timers.get(0).getValue();
      callback.call();
      callback.dispose();
      timers.remove(0);
    }
  }

  @Override
  public Timer createTimer()
  {
    return timer = new MockTimer();
  }

  @Test
  public void testSingleSetTimeout() throws InterruptedException
  {
    assertEquals(0, timer.timers.size());
    assertTrue(jsEngine.evaluate("this.isTimerFired").isUndefined());
    jsEngine.evaluate("setTimeout(function() {isTimerFired = 'OK';}, 1234)");
    assertTrue(jsEngine.evaluate("this.isTimerFired").isUndefined());
    assertEquals(1, timer.timers.size());
    assertEquals(1234, timer.timers.get(0).getKey().longValue());
    timer.processTimer();
    assertEquals("OK", jsEngine.evaluate("this.isTimerFired").asString());
  }

  @Test
  public void testSetTimeoutWithArgs() throws InterruptedException
  {
    jsEngine.evaluate("setTimeout(function(s) {foo = s;}, 100, 'foobar')");
    assertTrue(jsEngine.evaluate("this.foo").isUndefined());
    assertEquals(1, timer.timers.size());
    timer.processTimer();
    assertEquals("foobar", jsEngine.evaluate("this.foo").asString());
  }

  @Test
  public void testSetMultipleTimeouts() throws InterruptedException
  {
    jsEngine.evaluate("foo = []");
    jsEngine.evaluate("setTimeout(function(s) {foo.push('1');}, 100)");
    jsEngine.evaluate("setTimeout(function(s) {foo.push('2');}, 150)");
    assertEquals(2, timer.timers.size());
    timer.processTimer();
    assertEquals(1, timer.timers.size());
    timer.processTimer();
    assertEquals("1,2", jsEngine.evaluate("this.foo").asString());
  }
}
