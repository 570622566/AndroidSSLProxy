package me.lty.ssltest.mitm.impl.bootstrap;

import org.apache.httpcore.ExceptionLogger;
import org.apache.httpcore.HttpConnectionFactory;
import org.apache.httpcore.HttpHost;
import org.apache.httpcore.HttpServerConnection;
import org.apache.httpcore.config.SocketConfig;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLServerSocket;

import me.lty.ssltest.mitm.httpcore.HttpService;
import me.lty.ssltest.mitm.httpcore.Worker;

/**
 * Describe
 * <p>
 * Created on: 2018/1/17 上午11:23
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class HttpsRequestListener implements Runnable {

    private static final String TAG = HttpsRequestListener.class.getSimpleName();

    private final SocketConfig socketConfig;
    private final HttpHost targetHost;
    private final SSLServerSocket serversocket;
    private final HttpService httpService;
    private final HttpConnectionFactory<? extends HttpServerConnection> connectionFactory;
    private final ExceptionLogger exceptionLogger;
    private final ExecutorService executorService;
    private final AtomicBoolean terminated;

    public HttpsRequestListener(
            final SocketConfig socketConfig,
            final HttpHost targetHost,
            final SSLServerSocket serverSocket,
            final HttpService httpService,
            final HttpConnectionFactory<? extends HttpServerConnection>
                    connectionFactory,
            final ExceptionLogger exceptionLogger,
            final ExecutorService executorService) {

        this.socketConfig = socketConfig;
        this.serversocket = serverSocket;
        this.targetHost = targetHost;
        this.connectionFactory = connectionFactory;
        this.httpService = httpService;
        this.exceptionLogger = exceptionLogger;
        this.executorService = executorService;
        this.terminated = new AtomicBoolean(false);
    }

    @Override
    public void run() {
        try {
            final Socket socket = this.serversocket.accept();
            socket.setSoTimeout(this.socketConfig.getSoTimeout());
            socket.setKeepAlive(this.socketConfig.isSoKeepAlive());
            socket.setTcpNoDelay(this.socketConfig.isTcpNoDelay());
            if (this.socketConfig.getRcvBufSize() > 0) {
                socket.setReceiveBufferSize(this.socketConfig.getRcvBufSize());
            }
            if (this.socketConfig.getSndBufSize() > 0) {
                socket.setSendBufferSize(this.socketConfig.getSndBufSize());
            }
            if (this.socketConfig.getSoLinger() >= 0) {
                socket.setSoLinger(true, this.socketConfig.getSoLinger());
            }
            final HttpServerConnection conn = this.connectionFactory.createConnection(socket);
            final Worker worker = new Worker(
                    this.httpService,
                    conn,
                    this.targetHost,
                    this.exceptionLogger
            );
            worker.setUseSSL(true);
            this.executorService.execute(worker);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }
}
