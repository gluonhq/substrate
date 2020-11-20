/*
 * Copyright (c) 2020, Gluon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL GLUON BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gluonhq.helloandroid;

import android.graphics.Bitmap;
import android.os.Build;
import androidx.annotation.RequiresApi;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.StringReader;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class NativeWebView {

    private static final String TAG     = "GraalActivity";

    private static MainActivity instance;
    private WebView webView;
    private boolean inlayout = false;
    private boolean layoutStarted = false;
    private double width = 0;
    private double height = 0;
    private int x = 0;
    private int y = 0;
    private boolean visible = true;
    private String scriptResult;

    public NativeWebView() {
        Log.v(TAG, "NATIVEWEBVIEW constructor starts");
        instance = MainActivity.getInstance();
        instance.runOnUiThread(new Runnable () {
            public void run() {
                NativeWebView.this.webView = new WebView(instance);
                NativeWebView.this.webView.setWebChromeClient(new WebChromeClient());
                NativeWebView.this.webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);
                        Log.d(TAG, "Page started: " + url);
                        nativeStartURL(url);
                    }

                    @Override
                    public void onPageFinished(WebView view, final String url) {
                        Log.v(TAG, "Page finished: " + url);
                        NativeWebView.this.webView.evaluateJavascript("document.documentElement.innerHTML", new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String s) {
                                Properties p = new Properties();
                                try {
                                    p.load(new StringReader("innerHtmlKey=" + s));
                                } catch (Exception e) {

                                }
                                nativeFinishURL(url, p.getProperty("innerHtmlKey"));
                            }
                        });
                    }

                    @RequiresApi(api = Build.VERSION_CODES.M)
                    @Override
                    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                        Log.v(TAG, "LOAD onReceivedError: request: " + request.getMethod());
                        Log.v(TAG, "LOAD onReceivedError: errorResponse: " + error.getDescription());
                        nativeFailedURL(request.getUrl().toString());
                    }

                    @Override
                    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                        Log.v(TAG, "LOAD onReceivedHttpError: request: " + request.getMethod());
                        Log.v(TAG, "LOAD onReceivedHttpError: errorResponse: " + errorResponse.getReasonPhrase());
                        nativeFailedURL(request.getUrl().toString());
                    }

                });

                WebSettings webSettings = NativeWebView.this.webView.getSettings();
                // TODO, pass from Java
                webSettings.setJavaScriptEnabled(true);
                webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
                webSettings.setDomStorageEnabled(true);
                webSettings.setUseWideViewPort(true);
                webSettings.setLoadWithOverviewMode(true);

                Log.v(TAG, "NATIVEWEBVIEW wv = "+NativeWebView.this.webView);
                Log.v(TAG, "finally, NATIVEWEBVIEW wv = "+NativeWebView.this.webView);
            }
        });
        reLayout();
        Log.v(TAG, "NATIVEWEBVIEW constructor returns: "+this);
    }

    public void loadUrl(final String url) {
        Log.v(TAG, "in dalvik, loadUrl called wwith url = "+url+" and webView = "+this.webView);
        instance.runOnUiThread(new Runnable () {
            public void run() {
                NativeWebView.this.webView.loadUrl(url);
            }
        });
    }

    public String executeScript(final String script) {
        Log.v(TAG, "in dalvik, loadUrl called with script  and webView = "+this.webView);
        final CountDownLatch latch = new CountDownLatch(1);
        scriptResult = null;
        Runnable action = new Runnable() {
            @Override
            public void run() {
                NativeWebView.this.webView.evaluateJavascript(script, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        Log.v(TAG, "in dalvik, script result: " + s);
                        scriptResult = s;
                        latch.countDown();
                    }
                });
            }
        };
        webView.post(action);
        try {
            latch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.v(TAG, "in dalvik, loadUrl script result = " + scriptResult);
        return scriptResult;
    }

    private void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            Log.v(TAG, "in dalvik, set visible = " + visible);
            instance.runOnUiThread(new Runnable() {
                public void run() {
                    if (NativeWebView.this.visible) {
                        NativeWebView.this.webView.setVisibility(View.VISIBLE);
                    } else {
                        NativeWebView.this.webView.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    private void setX(double x) {
        if (this.x != (int) x) {
            this.x = (int) x;
            reLayout();
        }
    }

    private void setY(double y) {
        if (this.y != (int) y) {
            this.y = (int) y;
            reLayout();
        }
    }

    private void setWidth(double width) {
        if (this.width != width) {
            this.width = width;
            reLayout();
        }
    }

    private void setHeight(double height) {
        if (this.height != height){
            this.height = height;
            reLayout();
        }
    }

    private void reLayout() {
        Log.v(TAG, "relayout...");
        if (!inlayout) {
            if (!layoutStarted) {
                instance.runOnUiThread(new Runnable() {
                    public void run() {
                        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                Gravity.NO_GRAVITY);
                        MainActivity.getViewGroup().addView(webView, layout);
                        inlayout = true;
                    }
                });
            }
            layoutStarted = true;
        }
        instance.runOnUiThread(new Runnable () {
            public void run() {
                FrameLayout.LayoutParams layout =
                        (FrameLayout.LayoutParams) webView.getLayoutParams();
                layout.leftMargin = x;
                layout.topMargin = y;
                layout.width = (int) width;
                layout.height = (int) height;
                MainActivity.getViewGroup().updateViewLayout(webView, layout);
            }
        });

    }

    private native void nativeStartURL(String url);
    private native void nativeFinishURL(String url, String innerHTML);
    private native void nativeFailedURL(String url);
}