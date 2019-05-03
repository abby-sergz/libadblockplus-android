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

package org.adblockplus.libadblockplus.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.adblockplus.libadblockplus.HeaderEntry;
import org.json.JSONArray;

import android.content.Context;

public final class Utils
{
  private Utils()
  {
    //
  }

  public static String getTag(final Class<?> clazz)
  {
    return clazz.getSimpleName();
  }

  public static String stringListToJsonArray(List<String> list)
  {
    JSONArray array = new JSONArray();

    if (list != null)
    {
      for (String eachString : list)
      {
        if (eachString != null)
        {
          array.put(eachString);
        }
      }
    }

    return array.toString();
  }

  public static String readAssetAsString(final Context context,
                                         final String filename,
                                         final String charsetName)
      throws IOException
  {
    BufferedReader in = null;
    try
    {
      final InputStream is = context.getAssets().open(filename);
      return new String(toByteArray(is), charsetName);
    }
    finally
    {
      if (in != null)
      {
        try
        {
          in.close();
        }
        catch (IOException e)
        {
          // ignored
        }
      }
    }
  }

  public static String getUrlWithoutParams(String urlWithParams)
  {
    if (urlWithParams == null)
    {
      throw new IllegalArgumentException("URL can't be null");
    }

    int pos = urlWithParams.indexOf("?");
    return (pos >= 0 ? urlWithParams.substring(0, pos) : urlWithParams);
  }

  private static final int BUFFER_SIZE = 8192;

  /** Max array length on JVM. */
  private static final int MAX_ARRAY_LEN = Integer.MAX_VALUE - 8;

  /** Large enough to never need to expand, given the geometric progression of buffer sizes. */
  private static final int TO_BYTE_ARRAY_DEQUE_SIZE = 20;

  /**
   * Returns a byte array containing the bytes from the buffers already in {@code bufs} (which have
   * a total combined length of {@code totalLen} bytes) followed by all bytes remaining in the given
   * input stream.
   */
  private static byte[] toByteArrayInternal(final InputStream in,
                                            final Deque<byte[]> bufs,
                                            int totalLen)
      throws IOException
  {
    // Starting with an 8k buffer, double the size of each sucessive buffer. Buffers are retained
    // in a deque so that there's no copying between buffers while reading and so all of the bytes
    // in each new allocated buffer are available for reading from the stream.
    for (int bufSize = BUFFER_SIZE; totalLen < MAX_ARRAY_LEN; bufSize *= 2)
    {
      byte[] buf = new byte[Math.min(bufSize, MAX_ARRAY_LEN - totalLen)];
      bufs.add(buf);
      int off = 0;
      while (off < buf.length)
      {
        // always OK to fill buf; its size plus the rest of bufs is never more than MAX_ARRAY_LEN
        int r = in.read(buf, off, buf.length - off);
        if (r == -1)
        {
          return combineBuffers(bufs, totalLen);
        }
        off += r;
        totalLen += r;
      }
    }

    // read MAX_ARRAY_LEN bytes without seeing end of stream
    if (in.read() == -1)
    {
      // oh, there's the end of the stream
      return combineBuffers(bufs, MAX_ARRAY_LEN);
    }
    else
    {
      throw new OutOfMemoryError("Input is too large to fit in a byte array");
    }
  }

  /**
   * Reads all bytes from an input stream into a byte array. Does not close the stream.
   *
   * @param in the input stream to read from
   * @return a byte array containing all the bytes from the stream
   * @throws IOException if an I/O error occurs
   */
  public static byte[] toByteArray(final InputStream in) throws IOException
  {
    return toByteArrayInternal(in, new ArrayDeque<byte[]>(TO_BYTE_ARRAY_DEQUE_SIZE), 0);
  }

  private static byte[] combineBuffers(final Deque<byte[]> bufs, final int totalLen)
  {
    byte[] result = new byte[totalLen];
    int remaining = totalLen;
    while (remaining > 0)
    {
      byte[] buf = bufs.removeFirst();
      int bytesToCopy = Math.min(remaining, buf.length);
      int resultOffset = totalLen - remaining;
      System.arraycopy(buf, 0, result, resultOffset, bytesToCopy);
      remaining -= bytesToCopy;
    }
    return result;
  }

  /**
   * Read all input stream into ByteBuffer that can be passed over JNI
   * @param inputStream input stream
   * @return byte buffer
   * @throws IOException
   */
  public static ByteBuffer readFromInputStream(final InputStream inputStream) throws IOException
  {
    try
    {
      final byte[] bufferBytes = Utils.toByteArray(inputStream);

      // WARNING: in order to be passed back to JNI one have to use `allocateDirect`
      final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferBytes.length);
      buffer.order(ByteOrder.nativeOrder());
      buffer.put(bufferBytes);
      buffer.rewind();

      return buffer;
    }
    finally
    {
      if (inputStream != null)
      {
        inputStream.close();
      }
    }
  }

  /**
   * Convert map to list of headers
   * @param list list of headers
   * @return map of headers
   */
  public static Map<String, String> convertToMap(final List<HeaderEntry> list)
  {
    final Map<String, String> map = new HashMap<String, String>(list.size());
    for (final HeaderEntry header : list)
    {
      map.put(header.getKey(), header.getValue());
    }
    return map;
  }

  /**
   * Convert list to map of headers
   * @param map map of headers
   * @return list of headers
   */
  public static List<HeaderEntry> convertToList(final Map<String, String> map)
  {
    final List<HeaderEntry> list = new ArrayList<HeaderEntry>(map.size());
    for (final Map.Entry<String, String> header : map.entrySet())
    {
      list.add(new HeaderEntry(header.getKey(), header.getValue()));
    }
    return list;
  }

  /**
   * Convert String to ByteBuffer
   * @param string string
   * @param charset charset
   * @return byte buffer (*direct* buffer, allocated with `ByteBuffer.allocateDirect`)
   */
  public static ByteBuffer stringToByteBuffer(final String string, final Charset charset)
  {
    final byte[] bytes = string.getBytes(charset);
    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bytes.length);
    byteBuffer.put(bytes);
    return byteBuffer;
  }

  /**
   * Convert ByteBuffer to byte array
   * @param buffer buffer
   * @return byte array
   */
  public static byte[] byteBufferToByteArray(final ByteBuffer buffer)
  {
    buffer.rewind();
    final byte[] bytes = new byte[buffer.capacity()];
    buffer.get(bytes);
    return bytes;
  }

  /**
   * Check if URL is absolute
   * @param url URL
   * @return URL is absolute
   * @throws URISyntaxException
   */
  public static boolean isAbsoluteUrl(final String url) throws URISyntaxException
  {
    final URI uri = new URI(url);
    return uri.isAbsolute();
  }

  /**
   * Build full absolute URL from base and relative URLs
   * @param baseUrl base URL
   * @param relativeUrl relative URL
   * @return full absolute URL
   */
  public static String getAbsoluteUrl(final String baseUrl, final String relativeUrl)
    throws MalformedURLException
  {
    return new URL(new URL(baseUrl), relativeUrl).toExternalForm();
  }
}
