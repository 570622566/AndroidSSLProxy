package me.lty.ssltest.mitm;

import android.util.LruCache;

import org.apache.httpcore.ConnectionReuseStrategy;
import org.apache.httpcore.HttpException;
import org.apache.httpcore.HttpHost;
import org.apache.httpcore.HttpRequest;
import org.apache.httpcore.HttpResponse;
import org.apache.httpcore.impl.DefaultBHttpClientConnection;
import org.apache.httpcore.impl.DefaultConnectionReuseStrategy;
import org.apache.httpcore.protocol.HttpContext;
import org.apache.httpcore.protocol.HttpProcessor;
import org.apache.httpcore.protocol.HttpProcessorBuilder;
import org.apache.httpcore.protocol.HttpRequestExecutor;
import org.apache.httpcore.protocol.RequestConnControl;
import org.apache.httpcore.protocol.RequestContent;
import org.apache.httpcore.protocol.RequestExpectContinue;
import org.apache.httpcore.protocol.RequestTargetHost;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
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
public class Firebase {

    private static Firebase mInstance;
    private final HttpProcessor mHttpProcessor;
    private final HttpRequestExecutor mHttpexecutor;

    private LruCache<String, DefaultBHttpClientConnection> connectionLruCache;
    private final ConnectionReuseStrategy connStrategy;
    private final SSLSocketFactory socketFactory;

    public static synchronized Firebase getInstance() {
        if (mInstance == null) {
            mInstance = new Firebase();
        }
        return mInstance;
    }

    private Firebase() {
        connectionLruCache = new LruCache<>(100);
        mHttpProcessor = HttpProcessorBuilder.create()
                                             .add(new RequestContent(true))
                                             .add(new RequestTargetHost())
                                             .add(new RequestConnControl())
                                             .add(new RequestExpectContinue(true)).build();
        mHttpexecutor = new HttpRequestExecutor();
        connStrategy = DefaultConnectionReuseStrategy.INSTANCE;

        SSLContext tls = null;
        try {
            tls = SSLContext.getInstance("TLS");
            tls.init(null, null, new SecureRandom());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        socketFactory = tls.getSocketFactory();
    }


    public HttpResponse openFire(HttpRequest request, HttpContext context) {
        HttpHost targetHost = (HttpHost) context.getAttribute("targetHost");

        final String schemeName = targetHost.getSchemeName();
        String host = targetHost.toHostString();
        String hostName = targetHost.getHostName();
        int hostPort = targetHost.getPort();

        DefaultBHttpClientConnection conn = connectionLruCache.get(host);
        if (conn == null || !conn.isOpen()) {
            try {
                conn = new DefaultBHttpClientConnection(8 * 1024);
                Socket socket;
                if ("https".equals(schemeName)) {
                    socket = socketFactory.createSocket(hostName, hostPort);
                } else {
                    socket = new Socket(InetAddress.getByName(host), hostPort);
                }
                conn.bind(socket);
            } catch (IOException e) {
                e.printStackTrace();
            }
            connectionLruCache.put(host, conn);
        }

        try {
            mHttpexecutor.preProcess(request, mHttpProcessor, context);
            HttpResponse realResponse = mHttpexecutor.execute(request, conn, context);
            mHttpexecutor.postProcess(realResponse, mHttpProcessor, context);

            if (!connStrategy.keepAlive(realResponse, context)) {
                conn.close();
                connectionLruCache.remove(host);
            } else {
                System.out.println("Connection kept alive...");
            }

            return realResponse;
        } catch (HttpException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            conn.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        connectionLruCache.remove(host);
        return null;
    }

}
