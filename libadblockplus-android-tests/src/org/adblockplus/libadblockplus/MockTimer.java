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

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MockTimer implements Timer
{
  public List<Map.Entry<Long, Callback>> timers = new LinkedList<Map.Entry<Long, Callback>>();

  @Override
  public void setTimer(long timeoutMsec, Callback callback)
  {
    timers.add(new AbstractMap.SimpleEntry<Long, Callback>(timeoutMsec, callback));
  }

  public void processNextTimer()
  {
    Callback callback = timers.get(0).getValue();
    callback.call();
    callback.dispose();
    timers.remove(0);
  }

  public void processImmediateTimers()
  {
    // Callback.call can add a new timer what breaks the iterator (ConcurrentModificationException),
    // see iterator docs.
    Map.Entry<Long, Callback> nullTimer = null;
    do
    {
      nullTimer = null;
      for (Iterator<Map.Entry<Long, Callback>> timerIterator = timers.listIterator(); timerIterator.hasNext(); )
      {
        Map.Entry<Long, Callback> timer = timerIterator.next();
        if (timer.getKey() == 0)
        {
          nullTimer = timer;
          timerIterator.remove();
          break;
        }
      }
      if (nullTimer != null)
      {
        nullTimer.getValue().call();
        nullTimer.getValue().dispose();
      }
    } while(nullTimer != null);
  }
}
