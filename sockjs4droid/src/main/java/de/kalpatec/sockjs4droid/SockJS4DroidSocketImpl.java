/*
 * Copyright 2016 Karl Pauls (karlpauls@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.kalpatec.sockjs4droid;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URL;


/**
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
class SockJS4DroidSocketFactoryImpl implements SockJS4Droid.SockJS4DroidSocketFactory
{
    private static final int INIT = 0;

    private static final int SEND = 2;

    private static final int CLOSE = 4;

    private static final String PAGE_START = "<html><head>";

    private static final String SOCKET_TEMPLATE =
            "<script>" +
                    "var sock = new SockJS('%s'%s);" +
                    "sock.onopen = function() {" +
                        "callback.callOnOpen();" +
                    "};" +
                    "sock.onmessage = function(e) {" +
                        "callback.callOnMessage(e.data);" +
                    "};" +
                    "sock.onclose = function(e) {" +
                        "callback.callOnClose(e.code, e.reason);" +
                    "};" +
            "</script>";

    private static final String PAGE_END = "</head></html>";

    private final Context m_context;
    private final String m_sockJS;

    SockJS4DroidSocketFactoryImpl(String url, Context context) throws IOException
    {
        m_context = context;
        m_sockJS = "<script src='data:application/javascript;base64," + base64URL(url) + "'></script>";
    }

    public SockJS4Droid.SockJS4DroidSocket createSocket(String url, Handler handler) throws IOException
    {
        return createSocket(url, handler, null);
    }

    public SockJS4Droid.SockJS4DroidSocket createSocket(String url, Handler handler, String options) throws IOException
    {
        return new SockJS4DroidSocketImpl(url, handler, options, createHandlerThread().getLooper()).init();
    }

    private class SockJS4DroidSocketImpl extends Handler implements SockJS4Droid.SockJS4DroidSocket
    {
        private final Handler m_handler;
        private final String m_url;
        private final String m_options;

        private WebView m_webView;
        private boolean m_initDone = false;
        private boolean m_isClosed = false;

        private SockJS4DroidSocketImpl(String url, Handler handler, String options, Looper looper)
        {
            super(looper);
            m_url = url;
            m_options = options;
            m_handler = handler;
        }

        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case INIT:
                    m_webView = new WebView(m_context);
                    m_webView.getSettings().setJavaScriptEnabled(true);
                    m_webView.setVisibility(View.GONE);
                    m_webView.setWillNotDraw(true);
                    m_webView.addJavascriptInterface(this, "callback");
                    onInit();
                    m_webView.loadDataWithBaseURL(m_url, (PAGE_START + m_sockJS + String.format(SOCKET_TEMPLATE, m_url, m_options != null ? "," + m_options : "") + PAGE_END), "text/html", "UTF-8", null);
                    break;
                case CLOSE:
                    m_webView.evaluateJavascript("sock.close();", null);
                    break;
                case SEND:
                    m_webView.evaluateJavascript("sock.send('" + msg.getData().getString("message") + "');", null);
                    break;
                default:
                    break;
            }
        }

        @Override
        public synchronized boolean isOpen()
        {
            return m_initDone && !m_isClosed;
        }

        @Override
        public synchronized void close() throws Exception
        {
            if (isOpen()) {
                Message m = obtainMessage(CLOSE);
                sendMessage(m);
                while (isOpen()) {
                    wait();
                }
                getLooper().quit();
            }
            m_isClosed = true;
        }

        private synchronized void onInit()
        {
            m_initDone = true;
            notifyAll();
        }

        private synchronized SockJS4DroidSocketImpl init() throws InterruptedIOException
        {
            while (!m_initDone)
            {
                sendEmptyMessage(INIT);

                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new InterruptedIOException(e.getMessage());
                }
            }

            return this;
        }

        @Override
        public SockJS4Droid.SockJS4DroidSocket send(String message)
        {
            Message m = obtainMessage(SEND);
            Bundle bundle = new Bundle();
            bundle.putString("message", message);
            m.setData(bundle);
            sendMessage(m);
            return this;
        }

        @JavascriptInterface
        public synchronized void callOnOpen() {
            if (isOpen())
            {
                Message message = m_handler.obtainMessage(ON_OPEN);

                m_handler.sendMessage(message);
            }
        }

        @JavascriptInterface
        public synchronized void callOnMessage(String data)
        {
            if (isOpen())
            {
                Message message = m_handler.obtainMessage(ON_MESSAGE);
                Bundle bundle = new Bundle();
                bundle.putString(DATA, data);
                message.setData(bundle);
                m_handler.sendMessage(message);
            }
        }

        @JavascriptInterface
        public synchronized void callOnClose(int code, String reason)
        {
            if (isOpen())
            {
                m_isClosed = true;
                Message message = m_handler.obtainMessage(ON_CLOSE);
                Bundle bundle = new Bundle();
                bundle.putInt(CODE, code);
                bundle.putString(REASON, reason);
                message.setData(bundle);
                m_handler.sendMessage(message);
                notifyAll();
            }
        }

    }

    private static HandlerThread createHandlerThread()
    {
        HandlerThread handler = new HandlerThread("SockJS4Droid");
        handler.start();

        return handler;
    }

    private static String base64URL(String urlString) throws IOException
    {
        try(ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            InputStream input = new URL(urlString).openStream())
        {
            byte[] tmp = new byte[8 * 1024];
            for (int i = input.read(tmp); i != -1;i = input.read(tmp))
            {
                buffer.write(tmp, 0, i);
            }
            return Base64.encodeToString(buffer.toByteArray(), Base64.DEFAULT);
        }
    }
}
