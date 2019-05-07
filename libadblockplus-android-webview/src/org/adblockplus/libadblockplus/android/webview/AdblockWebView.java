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

package org.adblockplus.libadblockplus.android.webview;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;  // makes android min version to be 21
import android.webkit.WebResourceResponse;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.adblockplus.libadblockplus.AdblockPlusException;
import org.adblockplus.libadblockplus.FilterEngine;
import org.adblockplus.libadblockplus.HeaderEntry;
import org.adblockplus.libadblockplus.HttpClient;
import org.adblockplus.libadblockplus.HttpRequest;
import org.adblockplus.libadblockplus.ServerResponse;
import org.adblockplus.libadblockplus.Subscription;
import org.adblockplus.libadblockplus.android.AdblockEngine;
import org.adblockplus.libadblockplus.android.AdblockEngineProvider;
import org.adblockplus.libadblockplus.android.SingleInstanceEngineProvider;
import org.adblockplus.libadblockplus.android.Utils;
import org.adblockplus.libadblockplus.sitekey.PublicKeyHolderImpl;
import org.adblockplus.libadblockplus.sitekey.SiteKeyException;
import org.adblockplus.libadblockplus.sitekey.SiteKeysConfiguration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.adblockplus.libadblockplus.android.Utils.convertToList;
import static org.adblockplus.libadblockplus.android.Utils.convertToMap;

/**
 * WebView with ad blocking
 */
public class AdblockWebView extends WebView
{
  private static final String TAG = Utils.getTag(AdblockWebView.class);

  protected static final String HEADER_REFERRER = "Referer";
  protected static final String HEADER_REQUESTED_WITH = "X-Requested-With";
  protected static final String HEADER_REQUESTED_WITH_XMLHTTPREQUEST = "XMLHttpRequest";
  protected static final String HEADER_LOCATION = "Location";
  protected static final String HEADER_USER_AGENT = "User-Agent";

  // use low-case strings as in WebResponse all header keys are lowered-case
  protected static final String HEADER_SITEKEY = "x-adblock-key";
  protected static final String HEADER_CONTENT_TYPE = "content-type";
  protected static final String HEADER_CONTENT_ENCODING = "content-encoding";

  private static final String ASSETS_CHARSET_NAME = "UTF-8";
  private static final String BRIDGE_TOKEN = "{{BRIDGE}}";
  private static final String DEBUG_TOKEN = "{{DEBUG}}";
  private static final String HIDE_TOKEN = "{{HIDE}}";
  private static final String HIDDEN_TOKEN = "{{HIDDEN_FLAG}}";
  private static final String BRIDGE = "jsBridge";
  private static final String EMPTY_ELEMHIDE_ARRAY_STRING = "[]";

  private RegexContentTypeDetector contentTypeDetector = new RegexContentTypeDetector();
  private boolean adblockEnabled = true;
  private boolean debugMode;
  private AdblockEngineProvider provider;
  private Integer loadError;
  private WebChromeClient extWebChromeClient;
  private WebViewClient extWebViewClient;
  private WebViewClient intWebViewClient;
  private Map<String, String> url2Referrer = Collections.synchronizedMap(new HashMap<String, String>());
  private String url;
  private String injectJs;
  private CountDownLatch elemHideLatch;
  private String elemHideSelectorsString;
  private Object elemHideThreadLockObject = new Object();
  private ElemHideThread elemHideThread;
  private boolean loading;
  private String elementsHiddenFlag;
  private SiteKeysConfiguration siteKeysConfiguration;

  public AdblockWebView(Context context)
  {
    super(context);
    initAbp();
  }

  public AdblockWebView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    initAbp();
  }

  public AdblockWebView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    initAbp();
  }

  public SiteKeysConfiguration getSiteKeysConfiguration()
  {
    return siteKeysConfiguration;
  }

  public void setSiteKeysConfiguration(final SiteKeysConfiguration siteKeysConfiguration)
  {
    this.siteKeysConfiguration = siteKeysConfiguration;
  }

  public boolean isAdblockEnabled()
  {
    return adblockEnabled;
  }

  private void applyAdblockEnabled()
  {
    super.setWebViewClient(adblockEnabled ? intWebViewClient : extWebViewClient);
    super.setWebChromeClient(adblockEnabled ? intWebChromeClient : extWebChromeClient);
  }

  public void setAdblockEnabled(boolean adblockEnabled)
  {
    this.adblockEnabled = adblockEnabled;
    applyAdblockEnabled();
  }

  @Override
  public void setWebChromeClient(WebChromeClient client)
  {
    extWebChromeClient = client;
    applyAdblockEnabled();
  }

  public boolean isDebugMode()
  {
    return debugMode;
  }

  /**
   * Set to true to see debug log output int AdblockWebView and JS console
   * Should be set before first URL loading if using internal AdblockEngineProvider
   * @param debugMode is debug mode
   */
  public void setDebugMode(boolean debugMode)
  {
    this.debugMode = debugMode;
  }

  private void d(String message)
  {
    if (debugMode)
    {
      Log.d(TAG, message);
    }
  }

  private void w(String message)
  {
    if (debugMode)
    {
      Log.w(TAG, message);
    }
  }

  private void e(String message, Throwable t)
  {
    Log.e(TAG, message, t);
  }

  private void e(String message)
  {
    Log.e(TAG, message);
  }

  private String readScriptFile(String filename) throws IOException
  {
    return Utils
        .readAssetAsString(getContext(), filename, ASSETS_CHARSET_NAME)
        .replace(BRIDGE_TOKEN, BRIDGE)
        .replace(DEBUG_TOKEN, (debugMode ? "" : "//"))
        .replace(HIDDEN_TOKEN, elementsHiddenFlag);
  }

  private void runScript(String script)
  {
    d("runScript started");
    evaluateJavascript(script, null);
    d("runScript finished");
  }

  public void setProvider(final AdblockEngineProvider provider)
  {
    if (this.provider != null && provider != null && this.provider == provider)
    {
      return;
    }

    final Runnable setRunnable = new Runnable()
    {
      @Override
      public void run()
      {
        AdblockWebView.this.provider = provider;
        if (AdblockWebView.this.provider != null)
        {
          AdblockWebView.this.provider.retain(true); // asynchronously
        }
      }
    };

    if (this.provider != null)
    {
      // as adblockEngine can be busy with elemhide thread we need to use callback
      this.dispose(setRunnable);
    }
    else
    {
      setRunnable.run();
    }
  }

  private WebChromeClient intWebChromeClient = new WebChromeClient()
  {
    @Override
    public void onReceivedTitle(WebView view, String title)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedTitle(view, title);
      }
    }

    @Override
    public void onReceivedIcon(WebView view, Bitmap icon)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedIcon(view, icon);
      }
    }

    @Override
    public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReceivedTouchIconUrl(view, url, precomposed);
      }
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onShowCustomView(view, callback);
      }
    }

    @Override
    public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onShowCustomView(view, requestedOrientation, callback);
      }
    }

    @Override
    public void onHideCustomView()
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onHideCustomView();
      }
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                  Message resultMsg)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
      }
      else
      {
        return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
      }
    }

    @Override
    public void onRequestFocus(WebView view)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onRequestFocus(view);
      }
    }

    @Override
    public void onCloseWindow(WebView window)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onCloseWindow(window);
      }
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsAlert(view, url, message, result);
      }
      else
      {
        return super.onJsAlert(view, url, message, result);
      }
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsConfirm(view, url, message, result);
      }
      else
      {
        return super.onJsConfirm(view, url, message, result);
      }
    }

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                              JsPromptResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsPrompt(view, url, message, defaultValue, result);
      }
      else
      {
        return super.onJsPrompt(view, url, message, defaultValue, result);
      }
    }

    @Override
    public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsBeforeUnload(view, url, message, result);
      }
      else
      {
        return super.onJsBeforeUnload(view, url, message, result);
      }
    }

    @Override
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long quota,
                                        long estimatedDatabaseSize, long totalQuota,
                                        WebStorage.QuotaUpdater quotaUpdater)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onExceededDatabaseQuota(url, databaseIdentifier, quota,
            estimatedDatabaseSize, totalQuota, quotaUpdater);
      }
      else
      {
        super.onExceededDatabaseQuota(url, databaseIdentifier, quota,
            estimatedDatabaseSize, totalQuota, quotaUpdater);
      }
    }

    @Override
    public void onReachedMaxAppCacheSize(long requiredStorage, long quota,
                                         WebStorage.QuotaUpdater quotaUpdater)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
      }
      else
      {
        super.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
      }
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin,
                                                   GeolocationPermissions.Callback callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onGeolocationPermissionsShowPrompt(origin, callback);
      }
      else
      {
        super.onGeolocationPermissionsShowPrompt(origin, callback);
      }
    }

    @Override
    public void onGeolocationPermissionsHidePrompt()
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onGeolocationPermissionsHidePrompt();
      }
      else
      {
        super.onGeolocationPermissionsHidePrompt();
      }
    }

    @Override
    public boolean onJsTimeout()
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onJsTimeout();
      }
      else
      {
        return super.onJsTimeout();
      }
    }

    @Override
    public void onConsoleMessage(String message, int lineNumber, String sourceID)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.onConsoleMessage(message, lineNumber, sourceID);
      }
      else
      {
        super.onConsoleMessage(message, lineNumber, sourceID);
      }
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage)
    {
      d("JS: level=" + consoleMessage.messageLevel()
          + ", message=\"" + consoleMessage.message() + "\""
          + ", sourceId=\"" + consoleMessage.sourceId() + "\""
          + ", line=" + consoleMessage.lineNumber());

      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onConsoleMessage(consoleMessage);
      }
      else
      {
        return super.onConsoleMessage(consoleMessage);
      }
    }

    @Override
    public Bitmap getDefaultVideoPoster()
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.getDefaultVideoPoster();
      }
      else
      {
        return super.getDefaultVideoPoster();
      }
    }

    @Override
    public View getVideoLoadingProgressView()
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.getVideoLoadingProgressView();
      }
      else
      {
        return super.getVideoLoadingProgressView();
      }
    }

    @Override
    public void getVisitedHistory(ValueCallback<String[]> callback)
    {
      if (extWebChromeClient != null)
      {
        extWebChromeClient.getVisitedHistory(callback);
      }
      else
      {
        super.getVisitedHistory(callback);
      }
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress)
    {
      d("Loading progress=" + newProgress + "%");
      tryInjectJs();

      if (extWebChromeClient != null)
      {
        extWebChromeClient.onProgressChanged(view, newProgress);
      }
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                     FileChooserParams fileChooserParams)
    {
      if (extWebChromeClient != null)
      {
        return extWebChromeClient.onShowFileChooser(webView, filePathCallback, fileChooserParams);
      }
      else
      {
        return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
      }
    }
  };

  private void tryInjectJs()
  {
    if (loadError == null && injectJs != null)
    {
      d("Injecting script");
      runScript(injectJs);
    }
  }

  @Override
  public void setWebViewClient(WebViewClient client)
  {
    extWebViewClient = client;
    applyAdblockEnabled();
  }

  private void clearReferrers()
  {
    d("Clearing referrers");
    url2Referrer.clear();
  }

  /**
   * WebViewClient for API 21 and newer
   * (has Referrer since it overrides `shouldInterceptRequest(..., request)` with referrer)
   */
  private class AdblockWebViewClient extends WebViewClient
  {
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url)
    {
      if (extWebViewClient != null)
      {
        return extWebViewClient.shouldOverrideUrlLoading(view, url);
      }
      else
      {
        return super.shouldOverrideUrlLoading(view, url);
      }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon)
    {
      if (loading)
      {
        stopAbpLoading();
      }

      startAbpLoading(url);

      if (extWebViewClient != null)
      {
        extWebViewClient.onPageStarted(view, url, favicon);
      }
      else
      {
        super.onPageStarted(view, url, favicon);
      }
    }

    @Override
    public void onPageFinished(WebView view, String url)
    {
      loading = false;
      if (extWebViewClient != null)
      {
        extWebViewClient.onPageFinished(view, url);
      }
      else
      {
        super.onPageFinished(view, url);
      }
    }

    @Override
    public void onLoadResource(WebView view, String url)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onLoadResource(view, url);
      }
      else
      {
        super.onLoadResource(view, url);
      }
    }

    @Override
    public void onTooManyRedirects(WebView view, Message cancelMsg, Message continueMsg)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onTooManyRedirects(view, cancelMsg, continueMsg);
      }
      else
      {
        super.onTooManyRedirects(view, cancelMsg, continueMsg);
      }
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
    {
      e("Load error:" +
          " code=" + errorCode +
          " with description=" + description +
          " for url=" + failingUrl);
      loadError = errorCode;

      stopAbpLoading();

      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedError(view, errorCode, description, failingUrl);
      }
      else
      {
        super.onReceivedError(view, errorCode, description, failingUrl);
      }
    }

    @Override
    public void onFormResubmission(WebView view, Message dontResend, Message resend)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onFormResubmission(view, dontResend, resend);
      }
      else
      {
        super.onFormResubmission(view, dontResend, resend);
      }
    }

    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.doUpdateVisitedHistory(view, url, isReload);
      }
      else
      {
        super.doUpdateVisitedHistory(view, url, isReload);
      }
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedSslError(view, handler, error);
      }
      else
      {
        super.onReceivedSslError(view, handler, error);
      }
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedHttpAuthRequest(view, handler, host, realm);
      }
      else
      {
        super.onReceivedHttpAuthRequest(view, handler, host, realm);
      }
    }

    @Override
    public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event)
    {
      if (extWebViewClient != null)
      {
        return extWebViewClient.shouldOverrideKeyEvent(view, event);
      }
      else
      {
        return super.shouldOverrideKeyEvent(view, event);
      }
    }

    @Override
    public void onUnhandledKeyEvent(WebView view, KeyEvent event)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onUnhandledKeyEvent(view, event);
      }
      else
      {
        super.onUnhandledKeyEvent(view, event);
      }
    }

    @Override
    public void onScaleChanged(WebView view, float oldScale, float newScale)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onScaleChanged(view, oldScale, newScale);
      }
      else
      {
        super.onScaleChanged(view, oldScale, newScale);
      }
    }

    @Override
    public void onReceivedLoginRequest(WebView view, String realm, String account, String args)
    {
      if (extWebViewClient != null)
      {
        extWebViewClient.onReceivedLoginRequest(view, realm, account, args);
      }
      else
      {
        super.onReceivedLoginRequest(view, realm, account, args);
      }
    }

    protected WebResourceResponse shouldInterceptRequest(
        final WebView webview, final String url,
        final boolean isMainFrame, final boolean isXmlHttpRequest,
        final String requestMethod, final List<String> referrerChain,
        final Map<String, String> requestHeadersMap)
    {
      synchronized (provider.getEngineLock())
      {
        // if dispose() was invoke, but the page is still loading then just let it go
        if (provider.getCounter() == 0)
        {
          e("FilterEngine already disposed, allow loading");

          // allow loading by returning null
          return null;
        }
        else
        {
          provider.waitForReady();
        }

        if (isMainFrame)
        {
          // never blocking main frame requests, just subrequests
          w(url + " is main frame, allow loading");
        }
        else
        {
          final String siteKey = (siteKeysConfiguration != null
            ? PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
              .getAny(referrerChain, ""))
            : null);

          // whitelisted
          if (provider.getEngine().isDomainWhitelisted(url, referrerChain))
          {
            w(url + " domain is whitelisted, allow loading");
          }
          else if (provider.getEngine().isDocumentWhitelisted(url, referrerChain, siteKey))
          {
            w(url + " document is whitelisted, allow loading");
          }
          else
          {
            // determine the content
            FilterEngine.ContentType contentType;
            if (isXmlHttpRequest)
            {
              contentType = FilterEngine.ContentType.XMLHTTPREQUEST;
            }
            else
            {
              contentType = contentTypeDetector.detect(url);
              if (contentType == null)
              {
                contentType = FilterEngine.ContentType.OTHER;
              }
            }

            // check if we should block
            if (provider.getEngine().matches(url, contentType, referrerChain, siteKey))
            {
              w("Blocked loading " + url);

              // if we should block, return empty response which results in 'errorLoading' callback
              return new WebResourceResponse("text/plain", "UTF-8", null);
            }
            d("Allowed loading " + url);
          }
        }
      } // end of provider.getEngineLock()

      return fetchUrlAndCheckSiteKey(isMainFrame ? webview : null, url, requestHeadersMap, requestMethod);
    }

    private WebResourceResponse fetchUrlAndCheckSiteKey(final WebView webview, String url,
                                                        final Map<String, String> requestHeadersMap,
                                                        final String requestMethod)
    {
      if (siteKeysConfiguration == null ||
          (!requestMethod.equalsIgnoreCase(HttpClient.REQUEST_METHOD_GET) &&
              !requestMethod.equalsIgnoreCase(HttpClient.REQUEST_METHOD_POST)))
      {
        // for now we handle site key only for GET and POST requests
        return null;
      }

      final boolean autoFollowRedirect = webview == null;
      ServerResponse response;

      try
      {
        final HttpRequest request = new HttpRequest(url, requestMethod,
            convertToList(requestHeadersMap), autoFollowRedirect);
        response = siteKeysConfiguration.getHttpClient().request(request);
      }
      catch (final AdblockPlusException e)
      {
        Log.e(TAG, "WebRequest failed", e);
        // allow WebView to continue, repeating the request and handling the response
        return null;
      }

      final ServerResponse.NsStatus status = response.getStatus();
      int statusCode = response.getResponseStatus();

      if (HttpClient.isRedirectCode(statusCode))
      {
        if (webview != null)
        {
          reloadWebViewUrl(webview, url, response);
        }
        return null;
      }

      if (response.getFinalUrl() != null)
      {
        d("Updating url to " + response.getFinalUrl() + ", was (" + url + ")");
        url = response.getFinalUrl();
      }

      for (HeaderEntry header : response.getResponseHeaders())
      {
        if (header.getKey().equals(HEADER_SITEKEY))
        {
          // verify signature and save public key to be used as sitekey for next requests
          try
          {
            if (siteKeysConfiguration.getSiteKeyVerifier().verify(
                url, requestHeadersMap.get(HEADER_USER_AGENT), header.getValue()))
            {
              d("Url " + url + " public key verified successfully");
            }
            else
            {
              e("Url " + url + " public key is not verified");
            }
          }
          catch (final SiteKeyException e)
          {
            e("Failed to verify sitekey header", e);
          }
          break;
        }
      }

      final Map<String, String> responseHeadersMap = convertToMap(response.getResponseHeaders());
      final String responseContentType = responseHeadersMap.get(HEADER_CONTENT_TYPE);
      String responseMimeType = null;
      String responseEncoding = null;
      if (responseContentType != null)
      {
        final int colonPos = responseContentType.indexOf(";");
        if (colonPos > 0)
        {
          responseMimeType = responseContentType.substring(0, colonPos);
          final int equalPos = responseContentType.indexOf("=");
          if ((equalPos > 0) && (equalPos < responseContentType.length() - 1))
          {
            responseEncoding = responseContentType.substring(equalPos + 1);
          }
        }
        else if (responseContentType.indexOf("/") > 0)
        {
          responseMimeType = responseContentType;
        }
      }
      if (responseEncoding == null)
      {
        responseEncoding = responseHeadersMap.get(HEADER_CONTENT_ENCODING);
      }

      if (response.getResponse() != null)
      {
        final byte[] buffer = Utils.byteBufferToByteArray(response.getResponse());
        final InputStream byteBufferInputStream = new ByteArrayInputStream(buffer);
        return new WebResourceResponse(
            responseMimeType, responseEncoding,
            statusCode, getReasonPhrase(status),
            responseHeadersMap, byteBufferInputStream);
      }
      return null;
    }

    private void reloadWebViewUrl(final WebView webview,
                                  final String url,
                                  final ServerResponse response)
    {
      String redirectedUrl = null;
      for (final HeaderEntry header : response.getResponseHeaders())
      {
        if (header.getKey().equalsIgnoreCase(HEADER_LOCATION) &&
            header.getValue() != null &&
            !header.getValue().isEmpty())
        {
          redirectedUrl = header.getValue();
          try
          {
            // check and handle relative url redirection
            if (!Utils.isAbsoluteUrl(redirectedUrl))
            {
              redirectedUrl = Utils.getAbsoluteUrl(url, redirectedUrl);
            }
          }
          catch (final Exception e)
          {
            Log.e(TAG, "Failed to build absolute redirect URL", e);
            redirectedUrl = null;
          }
          break;
        }
      }

      if (redirectedUrl != null)
      {
        final String finalUrl = redirectedUrl;
        // we need to reload webview url to make it aware of new new url after redirection
        webview.post(new Runnable()
        {
          @Override
          public void run()
          {
            webview.stopLoading();
            webview.loadUrl(finalUrl);
          }
        });
      }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request)
    {
      // here we just trying to fill url -> referrer map
      // blocking/allowing loading will happen in `shouldInterceptRequest(WebView,String)`
      String url = request.getUrl().toString();

      boolean isXmlHttpRequest =
          request.getRequestHeaders().containsKey(HEADER_REQUESTED_WITH) &&
              HEADER_REQUESTED_WITH_XMLHTTPREQUEST.equals(
                  request.getRequestHeaders().get(HEADER_REQUESTED_WITH));

      String referrer = request.getRequestHeaders().get(HEADER_REFERRER);
      if (referrer != null)
      {
        d("Header referrer for " + url + " is " + referrer);
        if (!url.equals(referrer))
        {
          url2Referrer.put(url, referrer);
        }
        else
        {
          w("Header referrer value is the same as url, skipping url2Referrer.put()");
        }
      }
      else
      {
        w("No referrer header for " + url);
      }

      // reconstruct frames hierarchy
      List<String> referrers = new ArrayList<>();
      String parentUrl = url;
      while ((parentUrl = url2Referrer.get(parentUrl)) != null)
      {
        if (referrers.contains(parentUrl))
        {
          w("Detected referrer loop, finished creating referrers list");
          break;
        }
        referrers.add(0, parentUrl);
      }

      return shouldInterceptRequest(view, url, request.isForMainFrame(),
          isXmlHttpRequest, request.getMethod(),
          referrers, request.getRequestHeaders());
    }
  }

  private String getReasonPhrase(ServerResponse.NsStatus status)
  {
    return status.name().replace("_", "");
  }

  private void initAbp()
  {
    addJavascriptInterface(this, BRIDGE);
    initClients();
    initRandom();
  }

  private void initRandom()
  {
    elementsHiddenFlag = "abp" + Math.abs(new Random().nextLong());
  }

  private void initClients()
  {
    intWebViewClient = new AdblockWebViewClient();
    applyAdblockEnabled();
  }

  private class ElemHideThread extends Thread
  {
    private String selectorsString;
    private CountDownLatch finishedLatch;
    private AtomicBoolean isFinished;
    private AtomicBoolean isCancelled;

    public ElemHideThread(CountDownLatch finishedLatch)
    {
      this.finishedLatch = finishedLatch;
      isFinished = new AtomicBoolean(false);
      isCancelled = new AtomicBoolean(false);
    }

    @Override
    public void run()
    {
      synchronized (provider.getEngineLock())
      {
        try
        {
          if (provider.getCounter() == 0)
          {
            w("FilterEngine already disposed");
            selectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
          }
          else
          {
            provider.waitForReady();
            List<String> referrerChain = new ArrayList<String>(1);
            referrerChain.add(url);
            String parentUrl = url;
            while ((parentUrl = url2Referrer.get(parentUrl)) != null)
            {
              referrerChain.add(parentUrl);
            }

            List<Subscription> subscriptions = provider
                .getEngine()
                .getFilterEngine()
                .getListedSubscriptions();

            try
            {
              d("Listed subscriptions: " + subscriptions.size());
              if (debugMode)
              {
                for (Subscription eachSubscription : subscriptions)
                {
                  d("Subscribed to "
                      + (eachSubscription.isDisabled() ? "disabled" : "enabled")
                      + " " + eachSubscription);
                }
              }
            }
            finally
            {
              for (Subscription eachSubscription : subscriptions)
              {
                eachSubscription.dispose();
              }
            }

            final String domain = provider.getEngine().getFilterEngine().getHostFromURL(url);
            if (domain == null)
            {
              e("Failed to extract domain from " + url);
              selectorsString = EMPTY_ELEMHIDE_ARRAY_STRING;
            }
            else
            {
              // elemhide
              d("Requesting elemhide selectors from AdblockEngine for " + url + " in " + this);

              final String siteKey = (siteKeysConfiguration != null
                ? PublicKeyHolderImpl.stripPadding(siteKeysConfiguration.getPublicKeyHolder()
                  .getAny(referrerChain, ""))
                : null);

              List<String> selectors = provider
                .getEngine()
                .getElementHidingSelectors(url, domain, referrerChain, siteKey);

              d("Finished requesting elemhide selectors, got " + selectors.size() + " in " + this);
              selectorsString = Utils.stringListToJsonArray(selectors);
            }
          }
        }
        finally
        {
          if (isCancelled.get())
          {
            w("This thread is cancelled, exiting silently " + this);
          }
          else
          {
            finish(selectorsString);
          }
        }
      }
    }

    private void onFinished()
    {
      finishedLatch.countDown();
      synchronized (finishedRunnableLockObject)
      {
        if (finishedRunnable != null)
        {
          finishedRunnable.run();
        }
      }
    }

    private void finish(String selectorsString)
    {
      isFinished.set(true);
      d("Setting elemhide string " + selectorsString.length() + " bytes");
      elemHideSelectorsString = selectorsString;

      onFinished();
    }

    private final Object finishedRunnableLockObject = new Object();
    private Runnable finishedRunnable;

    public void setFinishedRunnable(Runnable runnable)
    {
      synchronized (finishedRunnableLockObject)
      {
        this.finishedRunnable = runnable;
      }
    }

    public void cancel()
    {
      w("Cancelling elemhide thread " + this);
      if (isFinished.get())
      {
        w("This thread is finished, exiting silently " + this);
      }
      else
      {
        isCancelled.set(true);
        finish(EMPTY_ELEMHIDE_ARRAY_STRING);
      }
    }
  }

  private Runnable elemHideThreadFinishedRunnable = new Runnable()
  {
    @Override
    public void run()
    {
      synchronized (elemHideThreadLockObject)
      {
        w("elemHideThread set to null");
        elemHideThread = null;
      }
    }
  };

  private void initAbpLoading()
  {
    getSettings().setJavaScriptEnabled(true);
    buildInjectJs();
    ensureProvider();
  }

  private void ensureProvider()
  {
    // if AdblockWebView works as drop-in replacement for WebView 'provider' is not set.
    // Thus AdblockWebView is using SingleInstanceEngineProvider instance
    if (provider == null)
    {
      setProvider(new SingleInstanceEngineProvider(
          getContext(), AdblockEngine.BASE_PATH_DIRECTORY, debugMode));
    }
  }

  private void startAbpLoading(String newUrl)
  {
    d("Start loading " + newUrl);

    loading = true;
    loadError = null;
    url = newUrl;

    if (url != null)
    {
      // elemhide
      elemHideLatch = new CountDownLatch(1);
      synchronized (elemHideThreadLockObject)
      {
        elemHideThread = new ElemHideThread(elemHideLatch);
        elemHideThread.setFinishedRunnable(elemHideThreadFinishedRunnable);
        elemHideThread.start();
      }
    }
    else
    {
      elemHideLatch = null;
    }
  }

  private void buildInjectJs()
  {
    try
    {
      if (injectJs == null)
      {
        injectJs = readScriptFile("inject.js").replace(HIDE_TOKEN, readScriptFile("css.js"));
      }
    }
    catch (final IOException e)
    {
      e("Failed to read script", e);
    }
  }

  @Override
  public void goBack()
  {
    if (loading)
    {
      stopAbpLoading();
    }

    super.goBack();
  }

  @Override
  public void goForward()
  {
    if (loading)
    {
      stopAbpLoading();
    }

    super.goForward();
  }

  @Override
  public void reload()
  {
    initAbpLoading();

    if (loading)
    {
      stopAbpLoading();
    }

    super.reload();
  }

  @Override
  public void loadUrl(String url)
  {
    initAbpLoading();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadUrl(url);
  }

  @Override
  public void loadUrl(String url, Map<String, String> additionalHttpHeaders)
  {
    initAbpLoading();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadUrl(url, additionalHttpHeaders);
  }

  @Override
  public void loadData(String data, String mimeType, String encoding)
  {
    initAbpLoading();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadData(data, mimeType, encoding);
  }

  @Override
  public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding,
                                  String historyUrl)
  {
    initAbpLoading();

    if (loading)
    {
      stopAbpLoading();
    }

    super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
  }

  @Override
  public void stopLoading()
  {
    stopAbpLoading();
    super.stopLoading();
  }

  private void stopAbpLoading()
  {
    d("Stop abp loading");

    loading = false;
    clearReferrers();

    synchronized (elemHideThreadLockObject)
    {
      if (elemHideThread != null)
      {
        elemHideThread.cancel();
      }
    }
  }

  // warning: do not rename (used in injected JS by method name)
  @JavascriptInterface
  public String getElemhideSelectors()
  {
    if (elemHideLatch == null)
    {
      return EMPTY_ELEMHIDE_ARRAY_STRING;
    }
    else
    {
      try
      {
        // elemhide selectors list getting is started in startAbpLoad() in background thread
        d("Waiting for elemhide selectors to be ready");
        elemHideLatch.await();
        d("Elemhide selectors ready, " + elemHideSelectorsString.length() + " bytes");

        return elemHideSelectorsString;
      }
      catch (final InterruptedException e)
      {
        w("Interrupted, returning empty selectors list");
        return EMPTY_ELEMHIDE_ARRAY_STRING;
      }
    }
  }

  private void doDispose()
  {
    w("Disposing AdblockEngine");
    provider.release();
  }

  private class DisposeRunnable implements Runnable
  {
    private Runnable disposeFinished;

    private DisposeRunnable(Runnable disposeFinished)
    {
      this.disposeFinished = disposeFinished;
    }

    @Override
    public void run()
    {
      doDispose();

      if (disposeFinished != null)
      {
        disposeFinished.run();
      }
    }
  }

  /**
   * Dispose AdblockWebView and internal adblockEngine if it was created
   * If external AdblockEngine was passed using `setAdblockEngine()` it should be disposed explicitly
   * Warning: runnable can be invoked from background thread
   * @param disposeFinished runnable to run when AdblockWebView is disposed
   */
  public void dispose(final Runnable disposeFinished)
  {
    d("Dispose invoked");

    if (provider == null)
    {
      d("No internal AdblockEngineProvider created");
      return;
    }

    stopLoading();

    DisposeRunnable disposeRunnable = new DisposeRunnable(disposeFinished);
    synchronized (elemHideThreadLockObject)
    {
      if (elemHideThread != null)
      {
        w("Busy with elemhide selectors, delayed disposing scheduled");
        elemHideThread.setFinishedRunnable(disposeRunnable);
      }
      else
      {
        disposeRunnable.run();
      }
    }
  }
}
