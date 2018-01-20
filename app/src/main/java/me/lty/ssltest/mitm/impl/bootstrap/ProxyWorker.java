package me.lty.ssltest.mitm.impl.bootstrap;

import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import org.apache.httpcore.ExceptionLogger;
import org.apache.httpcore.HttpConnectionFactory;
import org.apache.httpcore.HttpHost;
import org.apache.httpcore.HttpServerConnection;
import org.apache.httpcore.config.SocketConfig;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.net.ssl.SSLServerSocket;

import me.lty.ssltest.Config;
import me.lty.ssltest.mitm.factory.MITMSSLSocketFactory;
import me.lty.ssltest.mitm.httpcore.HttpService;
import me.lty.ssltest.mitm.httpcore.WorkerPoolExecutor;
import me.lty.ssltest.mitm.io.CopyStreamRunnable;

/**
 * Describe
 * <p>
 * Created on: 2018/1/16 上午11:21
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class ProxyWorker implements Runnable {

    private static final String TAG = ProxyWorker.class.getSimpleName();

    public static final String ACCEPT_TIMEOUT_MESSAGE = "Listen time out";

    private final Socket localSocket;
    private final SocketConfig socketConfig;
    private final HttpService httpService;
    private final HttpConnectionFactory<? extends HttpServerConnection> connectionFactory;
    private final ExceptionLogger exceptionLogger;
    private final WorkerPoolExecutor httpworkerExecutor;
    private final WorkerPoolExecutor requestExecutor;
    private final LruCache<String, MITMSSLSocketFactory> sslSocketFactroyCache;

    private final Pattern connectPattern;
    private final Pattern otherPattern;

    private SSLServerSocket proxySSLServer;
    private ServerSocket proxyPlainServer;

    public ProxyWorker(Socket acceptSocket,
                       ServerSocket httpPlainServer,
                       SocketConfig socketConfig,
                       HttpService httpService,
                       HttpConnectionFactory<? extends HttpServerConnection> connectionFactory,
                       ExceptionLogger exceptionLogger,
                       WorkerPoolExecutor httpworkerExecutor,
                       WorkerPoolExecutor requestExecutor,
                       LruCache<String, MITMSSLSocketFactory> sslSocketFactroyCache)
            throws IOException, PatternSyntaxException {

        this.localSocket = acceptSocket;
        this.proxyPlainServer = httpPlainServer;
        this.socketConfig = socketConfig;
        this.httpService = httpService;
        this.connectionFactory = connectionFactory;
        this.exceptionLogger = exceptionLogger;
        this.httpworkerExecutor = httpworkerExecutor;
        this.requestExecutor = requestExecutor;
        this.sslSocketFactroyCache = sslSocketFactroyCache;

        connectPattern =
                Pattern.compile(
                        "^CONNECT[ \\t]+([^:]+):(\\d+).*\r\n\r\n",
                        Pattern.DOTALL
                );

        otherPattern =
                Pattern.compile(
                        "^([A-Z]*)[ \\t]+([a-zA-z]+://[^\\s]*).*\r\n\r\n",
                        Pattern.DOTALL
                );
    }

    @Override
    public void run() {
        final byte[] buffer = new byte[40960];

        try {
            final InputStream inputStream = localSocket.getInputStream();
            final OutputStream outputStream = localSocket.getOutputStream();
            final BufferedInputStream in =
                    new BufferedInputStream(
                            inputStream,
                            buffer.length
                    );
            in.mark(buffer.length);

            final int bytesRead = in.read(buffer);
            final String line = bytesRead > 0 ? new String(
                    buffer,
                    0,
                    bytesRead,
                    "US-ASCII"
            ) : "";

            Log.d(TAG, line);

            final Matcher connectMatcher =
                    connectPattern.matcher(line);
            final Matcher otherMatcher =
                    otherPattern.matcher(line);
            HttpHost targetHost;

            if (connectMatcher.find()) {
                //then we have a proxy CONNECT message!
                // Discard any other plaintext data the client sends us:
                while (in.read(buffer, 0, in.available()) > 0) {
                }

                String remoteHost = connectMatcher.group(1);
                int remotePort = Integer.parseInt(connectMatcher.group(2));
                targetHost = new HttpHost(remoteHost, remotePort,"https");
                String target = targetHost.toHostString();

                Log.d(TAG, "Establishing a new HTTPS proxy connection to " + target);
                Log.d(TAG, "Remote Server Cert CN= " + remoteHost);

                ServerSocket localProxy = createProxySSLServer(remoteHost);
                final Socket sslProxySocket = new Socket(
                        Config.PROXY_SERVER_LISTEN_HOST,
                        localProxy.getLocalPort()
                );

                SecurityRequestListener securityRequestListener = new SecurityRequestListener(
                        this.socketConfig,
                        targetHost,
                        getProxySSLServer(),
                        this.httpService,
                        this.connectionFactory,
                        this.exceptionLogger,
                        this.httpworkerExecutor
                );

                this.requestExecutor.execute(securityRequestListener);

                try {
                    Thread.sleep(15);
                } catch (Exception ignore) {
                }

                new Thread(
                        new CopyStreamRunnable(
                                localSocket,
                                sslProxySocket,
                                "Copy to proxy engine for " + target
                        ),
                        "Copy to proxy engine for " + target
                ).start();

                new Thread(
                        new CopyStreamRunnable(
                                sslProxySocket,
                                localSocket,
                                "Copy from proxy engine for " + target
                        ),
                        "Copy from proxy engine for " + target
                ).start();

                sendClientResponse(
                        outputStream,
                        "200 Connection established",
                        remoteHost,
                        remotePort
                );
            } else {
                if (otherMatcher.find()) {
                    String uri = otherMatcher.group(2);

                    URL url = new URL(uri);
                    String host = url.getHost();
                    int port = url.getPort() > 0 ? url.getPort() : url.getDefaultPort();

                    Log.d(TAG, "Establishing a new HTTP proxy connection to " + host + ":" + port);

                    targetHost = new HttpHost(host,port,"http");

                    PlainRequestListener plainRequestListener = new PlainRequestListener(
                            this.socketConfig,
                            targetHost,
                            this.proxyPlainServer,
                            this.httpService,
                            this.connectionFactory,
                            this.exceptionLogger,
                            this.httpworkerExecutor
                    );
                    requestExecutor.execute(plainRequestListener);

                    try {
                        Thread.sleep(15);
                    } catch (Exception ignore) {
                    }

                    //创建socket 连接到server 并将请求的信息写入
                    Socket socket = new Socket(
                            InetAddress.getLocalHost(),
                            this.proxyPlainServer.getLocalPort()
                    );
                    socket.getOutputStream().write(buffer, 0, bytesRead);

                    new Thread(
                            new CopyStreamRunnable(
                                    socket,
                                    localSocket,
                                    "Copy to proxy engine for " + uri
                            ),
                            "Copy to proxy engine for " + uri
                    ).start();
                } else {
                    Log.e(TAG, "Failed to determine proxy destination from message:");
                    Log.e(TAG, line);
                    sendClientResponse(
                            localSocket.getOutputStream(),
                            "501 Not Implemented",
                            "localhost",
                            Config.PROXY_SERVER_LISTEN_PORT
                    );
                }
            }
        } catch (InterruptedIOException e) {
            e.printStackTrace();
            System.err.println(ACCEPT_TIMEOUT_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public final ServerSocket createProxySSLServer(String realServerCN) throws Exception {
        if (TextUtils.isEmpty(realServerCN)) {
            return null;
        }
        MITMSSLSocketFactory ssf = null;

        if (sslSocketFactroyCache.get(realServerCN) == null) {
            System.out.println("Creating a new certificate for " + realServerCN);
            ssf = new MITMSSLSocketFactory(realServerCN, Config.getInstance().getCaConfig());
            sslSocketFactroyCache.put(realServerCN, ssf);
        } else {
            System.out.println("Found cached certificate for " + realServerCN);
            ssf = sslSocketFactroyCache.get(realServerCN);
        }
        proxySSLServer = (SSLServerSocket) ssf.createServerSocket(
                Config.PROXY_SERVER_LISTEN_HOST,
                0
        );
        return proxySSLServer;
    }

    private SSLServerSocket getProxySSLServer() {
        return this.proxySSLServer;
    }

    private void sendClientResponse(OutputStream out, String msg, String remoteHost, int
            remotePort) throws IOException {
        final StringBuffer response = new StringBuffer();
        response.append("HTTP/1.1 ").append(msg).append("\r\n");
        response.append("Host: " + remoteHost + ":" +
                                remotePort + "\r\n");
        response.append("Proxy-agent: CS255-MITMProxy/1.0\r\n");
        response.append("\r\n");
        out.write(response.toString().getBytes());
        out.flush();
    }

}
