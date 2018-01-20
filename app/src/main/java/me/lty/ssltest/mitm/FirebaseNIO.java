package me.lty.ssltest.mitm;

import android.util.LruCache;

import org.apache.httpcore.ConnectionReuseStrategy;
import org.apache.httpcore.HttpHost;
import org.apache.httpcore.HttpRequest;
import org.apache.httpcore.HttpResponse;
import org.apache.httpcore.HttpResponseFactory;
import org.apache.httpcore.concurrent.FutureCallback;
import org.apache.httpcore.config.ConnectionConfig;
import org.apache.httpcore.impl.DefaultBHttpClientConnection;
import org.apache.httpcore.impl.DefaultHttpResponseFactory;
import org.apache.httpcore.impl.nio.DefaultHttpClientIODispatch;
import org.apache.httpcore.impl.nio.DefaultNHttpClientConnectionFactory;
import org.apache.httpcore.impl.nio.SSLNHttpClientConnectionFactory;
import org.apache.httpcore.impl.nio.pool.BasicNIOConnFactory;
import org.apache.httpcore.impl.nio.pool.BasicNIOConnPool;
import org.apache.httpcore.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.httpcore.nio.NHttpConnectionFactory;
import org.apache.httpcore.nio.NHttpMessageParserFactory;
import org.apache.httpcore.nio.NHttpMessageWriterFactory;
import org.apache.httpcore.nio.protocol.BasicAsyncRequestProducer;
import org.apache.httpcore.nio.protocol.BasicAsyncResponseConsumer;
import org.apache.httpcore.nio.protocol.HttpAsyncRequestExecutor;
import org.apache.httpcore.nio.protocol.HttpAsyncRequester;
import org.apache.httpcore.nio.reactor.ConnectingIOReactor;
import org.apache.httpcore.nio.reactor.IOEventDispatch;
import org.apache.httpcore.nio.reactor.IOReactorException;
import org.apache.httpcore.nio.util.ByteBufferAllocator;
import org.apache.httpcore.nio.util.HeapByteBufferAllocator;
import org.apache.httpcore.params.BasicHttpParams;
import org.apache.httpcore.protocol.HttpContext;
import org.apache.httpcore.protocol.HttpProcessor;
import org.apache.httpcore.protocol.HttpProcessorBuilder;
import org.apache.httpcore.protocol.RequestConnControl;
import org.apache.httpcore.protocol.RequestContent;
import org.apache.httpcore.protocol.RequestExpectContinue;
import org.apache.httpcore.protocol.RequestTargetHost;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

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
public class FirebaseNIO {

    private static FirebaseNIO mInstance;
    private final HttpProcessor httpProcessor;
    private final HttpAsyncRequestExecutor httpAsyncRequestExecutor;
    private final HttpAsyncRequester httpAsyncRequester;

    private LruCache<String, DefaultBHttpClientConnection> connectionLruCache;
    private ConnectionReuseStrategy connStrategy;
    private SSLSocketFactory socketFactory;
    private BasicNIOConnPool nioConnPool;
    private ConnectingIOReactor ioReactor;

    public static synchronized FirebaseNIO getInstance() {
        if (mInstance == null) {
            mInstance = new FirebaseNIO();
        }
        return mInstance;
    }

    private FirebaseNIO() {
        this.connectionLruCache = new LruCache<>(100);
        this.httpProcessor = HttpProcessorBuilder.create()
                                                 .add(new RequestContent(true))
                                                 .add(new RequestTargetHost())
                                                 .add(new RequestConnControl())
                                                 .add(new RequestExpectContinue(true)).build();

        this.httpAsyncRequestExecutor = new HttpAsyncRequestExecutor();
        final IOEventDispatch ioEventDispatch = new DefaultHttpClientIODispatch<HttpAsyncRequestExecutor>(
                this.httpAsyncRequestExecutor,
                ConnectionConfig.DEFAULT
        );

        try {
            ioReactor = new DefaultConnectingIOReactor();
            this.nioConnPool = new BasicNIOConnPool(ioReactor,ConnectionConfig.DEFAULT);
            this.nioConnPool.setDefaultMaxPerRoute(2);
            this.nioConnPool.setMaxTotal(2);

            if (ioReactor == null){
                throw new NullPointerException("ioReactor is not init");
            }

            final Thread t = new Thread(new Runnable() {

                public void run() {
                    try {
                        // Ready to go!
                        ioReactor.execute(ioEventDispatch);
                    } catch (final InterruptedIOException ex) {
                        System.err.println("Interrupted");
                    } catch (final IOException e) {
                        System.err.println("I/O error: " + e.getMessage());
                    }
                    System.out.println("Shutdown");
                }

            });
            t.start();
        } catch (IOReactorException e) {
            e.printStackTrace();
        }

        this.httpAsyncRequester = new HttpAsyncRequester(this.httpProcessor);
    }

    private SSLContext initSSL() {
        SSLContext tls = null;
        try {
            tls = SSLContext.getInstance("TLS");
            tls.init(null, null, new SecureRandom());
            return tls;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void shutDownFirebase(){
        try {
            this.ioReactor.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public HttpResponse openFire(HttpRequest request, HttpContext context) {
        HttpHost targetHost = (HttpHost) context.getAttribute("targetHost");

        ResponseCallback callback = new ResponseCallback();

        this.httpAsyncRequester.execute(
                new BasicAsyncRequestProducer(targetHost, request),
                new BasicAsyncResponseConsumer(),
                this.nioConnPool,
                context, callback
        );

        while (true) {
            if (callback.breakLoop) {
                switch (callback.result) {
                    case 0:
                    case -1:
                        return null;
                    default:
                        return callback.realResponse;
                }
            }
            try {
                Thread.sleep(15);
            }catch (Exception e){
            }
        }
    }

    static class ResponseCallback implements FutureCallback<HttpResponse> {

        private int result = 0;
        boolean breakLoop = false;
        HttpResponse realResponse = null;

        @Override
        public void completed(final HttpResponse response) {
            realResponse = response;
            result = 1;
            breakLoop = true;
        }

        @Override
        public void failed(final Exception e) {
            result = -1;
            breakLoop = true;
            e.printStackTrace();
        }

        @Override
        public void cancelled() {
            result = 0;
            breakLoop = true;
        }
    }

}
