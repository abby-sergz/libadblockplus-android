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

import android.net.Uri;
import android.util.Log;

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.HttpRequest;
import org.adblockplus.libadblockplus.JsValue;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.ServerResponse.NsStatus;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.adblockplus.libadblockplus.android.Utils.readFromInputStream;

public class AndroidHttpClient extends HttpClient
{
  public final static String TAG = Utils.getTag(HttpClient.class);

  private final HashSet<String> subscriptionURLs = new HashSet<String>();

  protected static final String ENCODING_GZIP = "gzip";
  protected static final String ENCODING_IDENTITY = "identity";

  private final boolean compressedStream;
  private final String charsetName;

  /**
   * Ctor
   * @param compressedStream Request for gzip compressed stream from the server
   * @param charsetName Optional charset name for sending POST data
   */
  public AndroidHttpClient(final boolean compressedStream,
                           final String charsetName)
  {
    this.compressedStream = compressedStream;
    this.charsetName = charsetName;
  }

  public AndroidHttpClient()
  {
    this(true, "UTF-8");
  }

  protected void updateSubscriptionURLs(final FilterEngine engine)
  {
    for (final org.adblockplus.libadblockplus.Subscription s : engine.fetchAvailableSubscriptions())
    {
      try
      {
        JsValue jsUrl = s.getProperty("url");
        try
        {
          this.subscriptionURLs.add(jsUrl.toString());
        }
        finally
        {
          jsUrl.dispose();
        }
      }
      finally
      {
        s.dispose();
      }
    }
    JsValue jsPref = engine.getPref("subscriptions_exceptionsurl");
    try
    {
      this.subscriptionURLs.add(jsPref.toString());
    }
    finally
    {
      jsPref.dispose();
    }
  }

  @Override
  public ServerResponse request(final HttpRequest request)
  {
    final ServerResponse response = new ServerResponse();
    try
    {
      final URL url = new URL(request.getUrl());
      Log.d(TAG, "Downloading from: " + url);

      final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod(request.getMethod());

      if (request.getMethod().equalsIgnoreCase(REQUEST_METHOD_GET))
      {
        setGetRequestHeaders(request.getHeaders(), connection);
      }
      connection.setRequestProperty("Accept-Encoding",
        (compressedStream ? ENCODING_GZIP : ENCODING_IDENTITY));
      connection.setInstanceFollowRedirects(request.getFollowRedirect());
      connection.connect();

      if (request.getMethod().equalsIgnoreCase(REQUEST_METHOD_POST))
      {
        setPostRequestBody(request.getHeaders(), connection);
      }

      if (connection.getHeaderFields().size() > 0)
      {
        List<HeaderEntry> responseHeaders = new LinkedList<HeaderEntry>();
        for (Map.Entry<String, List<String>> eachEntry : connection.getHeaderFields().entrySet())
        {
          for (String eachValue : eachEntry.getValue())
          {
            if (eachEntry.getKey() != null && eachValue != null)
            {
              responseHeaders.add(new HeaderEntry(eachEntry.getKey().toLowerCase(), eachValue));
            }
          }
        }
        response.setResponseHeaders(responseHeaders);
      }
      try
      {
        int responseStatus = connection.getResponseCode();
        response.setResponseStatus(responseStatus);
        response.setStatus(!isSuccessCode(responseStatus) ? NsStatus.ERROR_FAILURE : NsStatus.OK);

        InputStream inputStream = isSuccessCode(responseStatus) ?
          connection.getInputStream() : connection.getErrorStream();

        if (inputStream != null && compressedStream && ENCODING_GZIP.equals(connection.getContentEncoding()))
        {
          inputStream = new GZIPInputStream(inputStream);
        }

        if (inputStream != null)
        {
          response.setResponse(readFromInputStream(inputStream));
        }

        if (!url.equals(connection.getURL()))
        {
          Log.d(TAG, "Url was redirected, from: " + url + ", to: " + connection.getURL());
          response.setFinalUrl(connection.getURL().toString());
        }
      }
      finally
      {
        connection.disconnect();
      }
      Log.d(TAG, "Downloading finished");
    }
    catch (final MalformedURLException e)
    {
      // MalformedURLException can be caused by wrong user input so we should not (re)throw it
      Log.e(TAG, "WebRequest failed", e);
      response.setStatus(NsStatus.ERROR_MALFORMED_URI);
    }
    catch (final UnknownHostException e)
    {
      // UnknownHostException can be caused by wrong user input so we should not (re)throw it
      Log.e(TAG, "WebRequest failed", e);
      response.setStatus(NsStatus.ERROR_UNKNOWN_HOST);
    }
    catch (final Throwable t)
    {
      Log.e(TAG, "WebRequest failed", t);
      throw new AdblockPlusException("WebRequest failed", t);
    }
    return response;
  }

  private void setPostRequestBody(final List<HeaderEntry> headers,
                                  final HttpURLConnection connection) throws IOException
  {
    final Uri.Builder builder = new Uri.Builder();
    for (final HeaderEntry header : headers)
    {
      builder.appendQueryParameter(header.getKey(), header.getValue());
    }
    final String query = builder.build().getEncodedQuery();

    OutputStream os = null;
    BufferedWriter writer = null;
    try
    {
      os = connection.getOutputStream();
      writer = new BufferedWriter(charsetName != null
          ? new OutputStreamWriter(os, charsetName)
          : new OutputStreamWriter(os));
      writer.write(query);
      writer.flush();
    }
    finally
    {
      if (writer != null)
      {
        writer.close();
      }

      if (os != null)
      {
        os.close();
      }
    }
  }

  private void setGetRequestHeaders(final List<HeaderEntry> headers,
                                    final HttpURLConnection connection)
  {
    for (final HeaderEntry header : headers)
    {
      connection.setRequestProperty(header.getKey(), header.getValue());
    }
  }
}
