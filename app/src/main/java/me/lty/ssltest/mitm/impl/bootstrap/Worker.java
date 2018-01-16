package me.lty.ssltest.mitm.impl.bootstrap;

import android.util.Log;
import android.util.LruCache;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import me.lty.ssltest.mitm.ClientAOutCopyStreamRunnable;
import me.lty.ssltest.mitm.ConnectionDetails;
import me.lty.ssltest.mitm.CopyStreamRunnable;
import me.lty.ssltest.mitm.filter.ProxyDataFilter;
import me.lty.ssltest.mitm.engine.ProxyEngine;
import me.lty.ssltest.mitm.factory.MITMSSLSocketFactory;
import me.lty.ssltest.mitm.factory.MITMSocketFactory;

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
public class Worker implements Runnable {

    /**
     * HTTPS proxy implementation.
     * <p>
     * A HTTPS proxy client first send a CONNECT message to the proxy
     * port. The proxy accepts the connection responds with a 200 OK,
     * which is the client's queue to send SSL data to the proxy. The
     * proxy just forwards it on to the server identified by the CONNECT
     * message.
     * <p>
     * The Java API presents a particular challenge: it allows sockets
     * to be either SSL or not SSL, but doesn't let them change their
     * stripes midstream. (In fact, if the JSSE support was stream
     * oriented rather than socket oriented, a lot of problems would go
     * away). To hack around this, we accept the CONNECT then blindly
     * proxy the rest of the stream through a special
     * ProxyEngine class (ProxySSLEngine) which is instantiated to
     * handle SSL.
     *
     * @author Srinivas Inguva
     */

    private static final String TAG = Worker.class.getSimpleName();

    public static final String ACCEPT_TIMEOUT_MESSAGE = "Listen time out";

    private String m_tempRemoteHost;
    private int m_tempRemotePort;

    private final Pattern m_httpsConnectPattern;

    private final InnerProxySSLEngine m_proxySSLEngine;

    private final Socket localSocket;

    public final MITMSocketFactory m_socketFactory;
    private final ProxyDataFilter m_requestFilter;
    private final ProxyDataFilter m_responseFilter;
    private final ConnectionDetails m_connectionDetails;

    private final PrintWriter m_outputWriter;

    public Worker(Socket acceptSocket,
                  MITMSSLSocketFactory sslSocketFactory,
                  ProxyDataFilter requestFilter,
                  ProxyDataFilter responseFilter,
                  String localHost,
                  int localPort,
                  LruCache<String, MITMSSLSocketFactory> ssfCache)
            throws IOException, PatternSyntaxException {
        // We set this engine up for handling plain HTTP and delegate
        // to a proxy for HTTPS.
        this.m_socketFactory = sslSocketFactory;
        this.m_requestFilter = requestFilter;
        this.m_responseFilter = responseFilter;
        this.m_connectionDetails = new ConnectionDetails(localHost, localPort, "", -1, false);

        this.m_outputWriter = requestFilter.getOutputPrintWriter();

        //Plaintext Socket with client (i.e. browser)
        localSocket = acceptSocket;

        m_httpsConnectPattern =
                Pattern.compile(
                        "^CONNECT[ \\t]+([^:]+):(\\d+).*\r\n\r\n",
                        Pattern.DOTALL
                );

        // When handling HTTPS proxies, we use our plain socket to
        // accept connections on. We suck the bit we understand off
        // the front and forward the rest through our proxy engine.
        // The proxy engine listens for connection attempts (which
        // come from us), then sets up a thread pair which pushes data
        // back and forth until either the server closes the
        // connection, or we do (in response to our client closing the
        // connection). The engine handles multiple connections by
        // spawning multiple thread pairs.

        assert sslSocketFactory != null;
        m_proxySSLEngine = new InnerProxySSLEngine(
                sslSocketFactory,
                requestFilter,
                responseFilter,
                ssfCache
        );
    }

    @Override
    public void run() {
        // Should be more than adequate.
        final byte[] buffer = new byte[40960];

        try {
            // Grab the first plaintext upstream buffer, which we're hoping is
            // a CONNECT message.
            final BufferedInputStream in =
                    new BufferedInputStream(
                            localSocket.getInputStream(),
                            buffer.length
                    );

            in.mark(buffer.length);

            // Read a buffer full.
            final int bytesRead = in.read(buffer);

            final String line = bytesRead > 0 ? new String(
                    buffer,
                    0,
                    bytesRead,
                    "US-ASCII"
            ) : "";

            final Matcher httpsConnectMatcher =
                    m_httpsConnectPattern.matcher(line);

            // 'grep' for CONNECT message and extract the remote server/port

            if (httpsConnectMatcher.find()) {//then we have a proxy CONNECT message!
                // Discard any other plaintext data the client sends us:
                while (in.read(buffer, 0, in.available()) > 0) {
                }

                final String remoteHost = httpsConnectMatcher.group(1);

                // Must be a port number by specification.
                final int remotePort = Integer.parseInt(httpsConnectMatcher.group(2));

                final String target = remoteHost + ":" + remotePort;

                Log.d(
                        TAG,
                        "[HTTPSProxyEngine] Establishing a new HTTPS proxy connection to " +
                                "" + target
                );

                m_tempRemoteHost = remoteHost;
                m_tempRemotePort = remotePort;

                SSLSocket remoteSocket;
                try {
                    //Lookup the "common name" field of the certificate from the remote server:
                    remoteSocket = (SSLSocket)
                            m_proxySSLEngine.getSSLSocketFactory()
                                            .createClientSocket(remoteHost, remotePort);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    // Try to be nice and send a reasonable error message to client
                    sendClientResponse(
                            localSocket.getOutputStream(),
                            "504 Gateway Timeout",
                            remoteHost,
                            remotePort
                    );
                    return;
                }
                Log.d(TAG, "[HTTPSProxyEngine] Remote Server Cert CN= " + remoteHost);
                //We've already opened the socket, so might as well keep using it:
                m_proxySSLEngine.setRemoteSocket(remoteSocket);

                //This is a CRUCIAL step:  we dynamically generate a new cert, based
                // on the remote server's CN, and return a reference to the internal
                // server socket that will make use of it.
                ServerSocket localProxy = m_proxySSLEngine.createServerSocket(remoteHost);

                //Kick off a new thread to send/recv data to/from the remote server.
                // Remote server's response data is made available via an internal
                // SSLServerSocket.  All this work is handled by the m_proxySSLEngine:
                new Thread(m_proxySSLEngine, "HTTPS proxy SSL engine").start();

                try {
                    Thread.sleep(10);
                } catch (Exception ignore) {
                }

                // Create a new socket connection to our proxy engine.
                final Socket sslProxySocket = new Socket(
                        m_connectionDetails.getLocalHost(),
                        localProxy.getLocalPort()
                );

                // Now set up a couple of threads to punt
                // everything we receive over localSocket to
                // sslProxySocket, and vice versa.
                final OutputStream out = localSocket.getOutputStream();

                new Thread(
                        new CopyStreamRunnable(
                                in,
                                sslProxySocket.getOutputStream(),
                                "Copy to proxy engine for " + target
                        ),
                        "Copy to proxy engine for " + target
                ).start();

                new Thread(
                        new ClientAOutCopyStreamRunnable(
                                sslProxySocket,
                                out,
                                "Copy from proxy engine for " + target
                        ),
                        "Copy from proxy engine for " + target
                ).start();

                // Send a 200 response to send to client. Client
                // will now start sending SSL data to localSocket.
                sendClientResponse(out, "200 Connection established", remoteHost, remotePort);
            } else { //Not a CONNECT request.. nothing we can do.
                Log.e(TAG, "Failed to determine proxy destination from message:");
                Log.e(TAG, line);
                sendClientResponse(
                        localSocket.getOutputStream(),
                        "501 Not Implemented",
                        "localhost",
                        m_connectionDetails.getLocalPort()
                );
            }
        } catch (InterruptedIOException e) {
            e.printStackTrace();
            System.err.println(ACCEPT_TIMEOUT_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    /*
     * Used to funnel data between a client (e.g. a web browser) and a
     * remote SSLServer, that the client is making a request to.
     *
     */
    private class InnerProxySSLEngine extends ProxyEngine {
        Socket remoteSocket = null;
        int timeout = 0;

        SSLServerSocket m_sslServerSocketA;
        private final LruCache<String, MITMSSLSocketFactory> mSsfCache;

        /*
         * NOTE: that port number 0, used below indicates a system-allocated,
         * dynamic port number.
         */
        InnerProxySSLEngine(MITMSSLSocketFactory socketFactory,
                            ProxyDataFilter requestFilter,
                            ProxyDataFilter responseFilter,
                            LruCache<String, MITMSSLSocketFactory> ssfCache)
                throws IOException {
            super(
                    socketFactory,
                    requestFilter,
                    responseFilter,
                    new ConnectionDetails(Worker.this.m_connectionDetails.getLocalHost(),
                                          0, "", -1, true
                    )
            );

            this.mSsfCache = ssfCache;
        }

        public final void setRemoteSocket(Socket s) {
            this.remoteSocket = s;
        }

        public final ServerSocket createServerSocket(String remoteServerCN) throws Exception {
            assert remoteServerCN != null;

            MITMSSLSocketFactory ssf = null;

            if (mSsfCache.get(remoteServerCN) == null) {
                //Instantiate a NEW SSLSocketFactory with a cert that's based on the remote
                // server's Common Name
                System.out.println("[HTTPSProxyEngine] Creating a new certificate for " +
                                           remoteServerCN);
                ssf = new MITMSSLSocketFactory(remoteServerCN);
                mSsfCache.put(remoteServerCN, ssf);
            } else {
                System.out.println("[HTTPSProxyEngine] Found cached certificate for " +
                                           remoteServerCN);
                ssf = mSsfCache.get(remoteServerCN);
            }
            m_sslServerSocketA = (SSLServerSocket) ssf.createServerSocket(
                    getConnectionDetails().getLocalHost(),
                    0
            );
            return m_sslServerSocketA;
        }

        public SSLServerSocket getSSLServerSocketA() {
            return m_sslServerSocketA;
        }

        /*
         * localSocket.get[In|Out]putStream() is data that's (indirectly)
         * being read from / written to the client.
         *
         * m_tempRemoteHost is the remote SSL Server.
         */
        @Override
        public void run() {
            try {
                final Socket localSocket = this.getSSLServerSocketA().accept();

                Log.d(TAG, "[HTTPSProxyEngine] New proxy proxy connection to " +
                        m_tempRemoteHost + ":" + m_tempRemotePort);

                this.launchThreadPair(localSocket, remoteSocket,
                                      localSocket.getInputStream(),
                                      localSocket.getOutputStream(),
                                      m_tempRemoteHost, m_tempRemotePort
                );
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
    }

}
