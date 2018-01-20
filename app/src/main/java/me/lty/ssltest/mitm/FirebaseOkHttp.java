package me.lty.ssltest.mitm;

import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import org.apache.httpcore.ConnectionReuseStrategy;
import org.apache.httpcore.Header;
import org.apache.httpcore.HttpEntity;
import org.apache.httpcore.HttpEntityEnclosingRequest;
import org.apache.httpcore.HttpHost;
import org.apache.httpcore.HttpRequest;
import org.apache.httpcore.HttpResponse;
import org.apache.httpcore.HttpVersion;
import org.apache.httpcore.ProtocolVersion;
import org.apache.httpcore.StatusLine;
import org.apache.httpcore.concurrent.FutureCallback;
import org.apache.httpcore.config.ConnectionConfig;
import org.apache.httpcore.entity.BasicHttpEntity;
import org.apache.httpcore.impl.DefaultBHttpClientConnection;
import org.apache.httpcore.impl.nio.DefaultHttpClientIODispatch;
import org.apache.httpcore.impl.nio.pool.BasicNIOConnPool;
import org.apache.httpcore.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.httpcore.message.BasicHttpResponse;
import org.apache.httpcore.message.BasicStatusLine;
import org.apache.httpcore.nio.protocol.BasicAsyncRequestProducer;
import org.apache.httpcore.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.httpcore.nio.protocol.HttpAsyncRequestExecutor;
import org.apache.httpcore.nio.protocol.HttpAsyncRequester;
import org.apache.httpcore.nio.reactor.ConnectingIOReactor;
import org.apache.httpcore.nio.reactor.IOEventDispatch;
import org.apache.httpcore.nio.reactor.IOReactorException;
import org.apache.httpcore.protocol.HttpContext;
import org.apache.httpcore.protocol.HttpProcessor;
import org.apache.httpcore.protocol.HttpProcessorBuilder;
import org.apache.httpcore.protocol.RequestConnControl;
import org.apache.httpcore.protocol.RequestContent;
import org.apache.httpcore.protocol.RequestExpectContinue;
import org.apache.httpcore.protocol.RequestTargetHost;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.BufferedSink;

/**
 * Describe
 * <p>
 * Created on: 2018/1/18 下午5:41
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class FirebaseOkHttp {

    private static FirebaseOkHttp mInstance;
    private final OkHttpClient okHttpClient;

    public static synchronized FirebaseOkHttp getInstance() {
        if (mInstance == null) {
            mInstance = new FirebaseOkHttp();
        }
        return mInstance;
    }

    private FirebaseOkHttp() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient.Builder builder = new OkHttpClient
                .Builder()
                .addInterceptor(logging)
                .proxy(Proxy.NO_PROXY);
        okHttpClient = builder.build();
    }

    public HttpResponse openFire(HttpRequest request, HttpContext context) {
        HttpHost targetHost = (HttpHost) context.getAttribute("targetHost");
        boolean isSecurity = true;
        String schemeName = "";
        try {
            isSecurity = (boolean) context.getAttribute("isSecurity");
            schemeName = targetHost.getSchemeName();
        } catch (Exception e) {
        }
        String url;
        if (!TextUtils.isEmpty(schemeName) && "https".equals(schemeName) || isSecurity) {
            url = "https://" + targetHost.getHostName() + ":" + targetHost.getPort() + request
                    .getRequestLine().getUri();
        } else {
            url = request.getRequestLine().getUri();
        }

        ByteArrayInputStream bais;
        RequestBody requestBody = null;

        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest request1 = (HttpEntityEnclosingRequest) request;
            HttpEntity request1Entity = request1.getEntity();
            final int contentLength = (int) request1Entity.getContentLength();
            final byte[] bytes = new byte[contentLength];
            try {
                InputStream content = request1Entity.getContent();
                if (content != null) {
                    content.read(bytes, 0, contentLength);
                    Header contentType = request1Entity.getContentType();
                    MediaType mediaType = null;
                    if (contentType != null) {
                        final String value = contentType.getValue();
                        mediaType = MediaType.parse(value);
                    }
                    requestBody = new DefaultRequestBody(mediaType, bytes,contentLength);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Log.wtf("LLYH",url);

        Request.Builder builder = new Request.Builder()
                .method(request.getRequestLine().getMethod(), requestBody)
                .url(url);
        Header[] allHeaders = request.getAllHeaders();
        for (int i = 0; i < allHeaders.length; i++) {
            builder.addHeader(allHeaders[i].getName(), allHeaders[i].getValue());
        }
        Request realRequest = builder.build();

        HttpResponse realResponse = null;
        try {
            Response response = okHttpClient.newCall(realRequest).execute();
            ResponseBody body = response.body();
            Headers headers = response.headers();
            int code = response.code();
            String message = response.message();

            BasicStatusLine statusLine = new BasicStatusLine(new HttpVersion(1, 1), code, message);
            realResponse = new BasicHttpResponse(statusLine);

            for (String name : headers.names()) {
                realResponse.setHeader(name, headers.get(name));
            }

            if (body != null) {
                byte[] bytes = body.bytes();
                int length = bytes.length;
                MediaType contentType = body.contentType();

                bais = new ByteArrayInputStream(bytes, 0, length);
                BasicHttpEntity entity = new BasicHttpEntity();
                entity.setContent(bais);
                entity.setContentLength(length);
                try {
                    entity.setContentType(contentType.toString());
                } catch (Exception e) {
                }

                realResponse.setEntity(entity);
                realResponse.addHeader("Content-Length", String.valueOf(length));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return realResponse;
    }

    static class DefaultRequestBody extends RequestBody {

        private MediaType mediaType = null;
        private byte[] bytes;
        private int contentLength;

        DefaultRequestBody(MediaType mediaType,byte[] bytes,int contentLength) {
            if (mediaType != null) {
                this.mediaType = mediaType;
            }
            this.bytes = bytes;
            this.contentLength = contentLength;
        }

        @Nullable
        @Override
        public MediaType contentType() {
            return mediaType;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            sink.write(bytes, 0, contentLength);
        }
    }

}
