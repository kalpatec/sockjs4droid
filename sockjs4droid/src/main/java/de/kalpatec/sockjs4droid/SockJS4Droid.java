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
import android.os.Handler;

import java.io.IOException;

/**
 * @author <a href="mailto:karlpauls@gmail.com">Karl Pauls (karlpauls@gmail.com)</a>
 */
public final class SockJS4Droid {
    public static SockJS4DroidSocketFactory createSocketFactory(Context context) throws IOException
    {
        return createSocketFactory("https://cdn.jsdelivr.net/sockjs/1/sockjs.min.js", context);
    }

    public static SockJS4DroidSocketFactory createSocketFactory(String sockJSURL, Context context) throws IOException
    {
        return new SockJS4DroidSocketFactoryImpl(sockJSURL, context);
    }

    public interface SockJS4DroidSocketFactory
    {
        public SockJS4DroidSocket createSocket(String url, Handler handler) throws IOException;

        public SockJS4DroidSocket createSocket(String url, Handler handler, String options) throws IOException;
    }

    public interface SockJS4DroidSocket extends AutoCloseable
    {
        public static final int ON_OPEN = 1;
        public static final int ON_MESSAGE = 2;
        public static final int ON_CLOSE = 4;
        public static final String CODE = "code";
        public static final String DATA = "data";
        public static final String REASON = "reason";

        public SockJS4DroidSocket send(String message);

        public void close() throws Exception;

        public boolean isOpen();
    }
}
