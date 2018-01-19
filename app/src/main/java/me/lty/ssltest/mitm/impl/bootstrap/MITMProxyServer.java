package me.lty.ssltest.mitm.impl.bootstrap;/*
Copyright 2007 Srinivas Inguva

Redistribution and use in source and binary forms, with or without modification, are permitted
provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of
    * conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, this list of
    * conditions and the following disclaimer in the documentation and/or other materials
    * provided with the distribution.
    * Neither the name of Stanford University nor the names of its contributors may be used to
    * endorse or promote products derived from this software without specific prior written
    * permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

import android.support.annotation.NonNull;
import android.util.Log;
import android.util.LruCache;

import org.apache.httpcore.ConnectionReuseStrategy;
import org.apache.httpcore.ExceptionLogger;
import org.apache.httpcore.HttpResponseFactory;
import org.apache.httpcore.config.SocketConfig;
import org.apache.httpcore.impl.DefaultBHttpServerConnectionFactory;
import org.apache.httpcore.impl.DefaultConnectionReuseStrategy;
import org.apache.httpcore.impl.DefaultHttpResponseFactory;
import org.apache.httpcore.protocol.HttpProcessor;
import org.apache.httpcore.protocol.HttpProcessorBuilder;
import org.apache.httpcore.protocol.ResponseConnControl;
import org.apache.httpcore.protocol.ResponseContent;
import org.apache.httpcore.protocol.ResponseDate;
import org.apache.httpcore.protocol.ResponseServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import me.lty.ssltest.Config;
import me.lty.ssltest.mitm.factory.MITMSSLSocketFactory;
import me.lty.ssltest.mitm.flow.DefaultDataFlow;
import me.lty.ssltest.mitm.httpcore.HttpService;
import me.lty.ssltest.mitm.httpcore.WorkerPoolExecutor;

//import me.lty.ssltest.mitm.engine.HTTPSProxyEngine;

/**
 * Main class for the Man In The Middle SSL proxy.  Delegates the real work
 * to HTTPSProxyEngine.
 * <p>
 * NOTE: This code was originally developed as a project for use in the CS255 course at Stanford,
 * taught by Professor Dan Boneh.
 *
 * @author Srinivas Inguva
 */

public class MITMProxyServer {

    private static final String TAG = MITMProxyServer.class.getSimpleName();

    private final String localHost = Config.PROXY_SERVER_LISTEN_HOST;
    private final int port;
    private final LruCache<String, MITMSSLSocketFactory> sslSocketFactoryCache;

    private ServerSocket serverSocket;
    private final HttpService httpService;
    private final SocketConfig socketConfig;
    private final ExceptionLogger exceptionLogger;
    private final WorkerPoolExecutor workerExecutorService;
    private final WorkerPoolExecutor sslRequestExecutor;
    private final DefaultBHttpServerConnectionFactory connectionFactory;

    public MITMProxyServer(Builder builder) {
        this.port = builder.port;
        sslSocketFactoryCache = builder.sslFactoryCache;

        Log.i(TAG, "Initializing SSL proxy with the parameters:" +
                "\n   Local host:       " + localHost +
                "\n   Local port:       " + port +
                "\n   (SSL setup could take a few seconds)");

        Log.i(TAG, "Proxy initialized, listening on port " + port);
        Log.e(TAG, "Could not initialize proxy:");

        final HttpProcessorBuilder b = HttpProcessorBuilder.create();
        String serverInfoCopy = "Apache-HttpCore/1.1";
        b.addAll(
                new ResponseDate(),
                new ResponseServer(serverInfoCopy),
                new ResponseContent(true),
                new ResponseConnControl()
        );
        HttpProcessor httpProcessorCopy = b.build();
        ConnectionReuseStrategy connStrategyCopy = DefaultConnectionReuseStrategy.INSTANCE;
        HttpResponseFactory responseFactoryCopy = DefaultHttpResponseFactory.INSTANCE;
        DefaultDataFlow proxyDataFlow = new DefaultDataFlow();
        httpService = new HttpService(
                httpProcessorCopy, connStrategyCopy, responseFactoryCopy, proxyDataFlow);

        this.connectionFactory = DefaultBHttpServerConnectionFactory.INSTANCE;

        this.socketConfig = SocketConfig.custom()
                                        .setSoKeepAlive(true)
                                        .setSoReuseAddress(false)
                                        .setSoTimeout(builder.timeOut)
                                        .setTcpNoDelay(false)
                                        .build();
        exceptionLogger = builder.exceptionLogger;

        ThreadGroup workerThreads = new ThreadGroup("HTTP-workers");
        workerExecutorService = new WorkerPoolExecutor(
                0, Integer.MAX_VALUE, 1L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new ThreadFactoryImpl("HTTP-worker", workerThreads)
        );
        sslRequestExecutor = new WorkerPoolExecutor(
                0, Integer.MAX_VALUE, 1L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new ThreadFactoryImpl("HTTPS-worker", workerThreads)
        );
    }

    public void start() throws IOException {
        this.serverSocket = new ServerSocket(this.port, 50);

        ThreadGroup threadGroup = new ThreadGroup("Proxy-workers");
        ProxyWorkerPoolExecutor proxyWorkerPoolExecutor = new ProxyWorkerPoolExecutor(
                0,
                Integer.MAX_VALUE,
                1L,
                TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new ThreadFactoryImpl(
                        "Proxy-worker",
                        threadGroup
                )
        );

        do {
            try {
                Socket finalAccept = this.serverSocket.accept();
                ProxyWorker proxyWorker = createWorker(finalAccept);
                proxyWorkerPoolExecutor.execute(proxyWorker);
            } catch (Exception e) {
                Log.e(TAG, "Could not initialize proxy:");
                e.printStackTrace();
            }
        } while (!this.serverSocket.isClosed());

        Log.i(TAG, "Engine exited");
    }

    @NonNull
    private ProxyWorker createWorker(Socket finalAccept) throws Exception {
        return new ProxyWorker(
                finalAccept,
                this.socketConfig,
                this.httpService,
                this.connectionFactory,
                this.exceptionLogger,
                this.workerExecutorService,
                this.sslRequestExecutor,
                this.sslSocketFactoryCache
        );
    }

    public static class Builder {

        private int port;
        private int timeOut;
        private ExceptionLogger exceptionLogger;

        private LruCache<String, MITMSSLSocketFactory> sslFactoryCache;

        public Builder() {

        }

        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        public Builder setTimeOut(int timeOut,TimeUnit timeUnit) {
            long timeoutMs = timeUnit.toMillis((long)timeOut);
            this.timeOut = (int)Math.min(timeoutMs, 2147483647L);
            return this;
        }

        public Builder setExceptionLogger(ExceptionLogger exceptionLogger) {
            this.exceptionLogger = exceptionLogger;
            return this;
        }

        public Builder setSslFactoryCache(LruCache<String, MITMSSLSocketFactory> sslFactoryCache) {
            this.sslFactoryCache = sslFactoryCache;
            return this;
        }

        public MITMProxyServer build() {
            return new MITMProxyServer(this);
        }
    }
}
