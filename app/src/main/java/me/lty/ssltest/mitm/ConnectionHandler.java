package me.lty.ssltest.mitm;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLSocket;

import iaik.asn1.ObjectID;
import iaik.asn1.structures.Name;
import me.lty.ssltest.mitm.engine.HTTPSProxyEngine;
import me.lty.ssltest.mitm.engine.ProxyEngine;
import me.lty.ssltest.mitm.engine.ProxySSLEngine;
import me.lty.ssltest.mitm.nanohttp.HttpRequest;

/**
 * Describe
 * <p>
 * Created on: 2018/1/13 上午11:38
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class ConnectionHandler implements Runnable {

    private static final String TAG = ConnectionHandler.class.getSimpleName();

    private AsyncRunner mAsyncRunner;
    private Socket mAcceptSocket;
    private ProxySSLEngine m_proxySSLEngine;

    public ConnectionHandler(AsyncRunner asyncRunner,Socket connection, ProxySSLEngine proxySSLEngine) {
        this.mAsyncRunner =asyncRunner;
        this.mAcceptSocket = connection;
        this.m_proxySSLEngine = proxySSLEngine;
    }

    @Override
    public void run(){
        try {
            InputStream inputStream = mAcceptSocket.getInputStream();
            HttpRequest httpRequest = new HttpRequest(inputStream);
            Log.e(TAG, httpRequest.toString());

            // 'grep' for CONNECT message and extract the remote server/port

            if ("CONNECT".equals(httpRequest.getMethod().name())) {
                //then we have a proxy CONNECT message!
                final String remoteHost = httpRequest.getRemoteHost();

                // Must be a port number by specification.
                final int remotePort = httpRequest.getRemotePort();

                final String target = remoteHost + ":" + remotePort;

                if (MITMProxyServer.debugFlag)
                    Log.d(TAG, "Establishing a new HTTPS proxy connection to " + target);

                //m_tempRemoteHost = remoteHost;
                //m_tempRemotePort = remotePort;
                m_proxySSLEngine.setTempRemote(remoteHost, remotePort);

                X509Certificate java_cert = null;
                SSLSocket remoteSocket = null;
                try {
                    if (remoteHost.contains("google")) {
                        return;
                    }
                    //Lookup the "common name" field of the certificate from the remote server:
                    remoteSocket = (SSLSocket)
                            m_proxySSLEngine.getSocketFactory()
                                            .createClientSocket(remoteHost, remotePort);
                    java_cert = (X509Certificate) remoteSocket.getSession()
                                                              .getPeerCertificates()[0];
                } catch (IOException ioe) {
                    ioe.printStackTrace(System.err);
                    // Try to be nice and send a reasonable error message to client
                    ProxyEngine.sendClientResponse(
                            mAcceptSocket.getOutputStream(),
                            "504 Gateway Timeout",
                            remoteHost,
                            remotePort
                    );
                    return;
                }

                //Use the IAIK X509Cert class, because it has a simple way to get the CN
                iaik.x509.X509Certificate cert = new iaik.x509.X509Certificate(
                        java_cert.getEncoded());
                Name n = (Name) cert.getSubjectDN();
                String serverCN = n.getRDN(ObjectID.commonName);

                if (MITMProxyServer.debugFlag)
                    Log.d(TAG, "Remote Server Cert CN= " + serverCN);

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
                    ignore.printStackTrace(System.err);
                }

                // Create a new socket connection to our proxy engine.
                final Socket sslProxySocket =
                        m_proxySSLEngine.getSocketFactory().createClientSocket(
                                m_proxySSLEngine.getConnectionDetails().getLocalHost(),
                                localProxy.getLocalPort()
                        );

                // Now set up a couple of threads to punt
                // everything we receive over localSocket to
                // sslProxySocket, and vice versa.
                new Thread(
                        new CopyStreamRunnable(inputStream, sslProxySocket.getOutputStream()),
                        "Copy to proxy engine for " + target
                ).start();

                final OutputStream out = mAcceptSocket.getOutputStream();

                new Thread(
                        new CopyStreamRunnable(
                                sslProxySocket.getInputStream(), out),
                        "Copy from proxy engine for " + target
                ).start();

                // Send a 200 response to send to client. Client
                // will now start sending SSL data to localSocket.
                HTTPSProxyEngine.sendClientResponse(out, "200 Connection established", remoteHost, remotePort);
            } else {
                //Not a CONNECT request

                //Log.e(TAG, "Failed to determine proxy destination from message:");
                HTTPSProxyEngine.sendClientResponse(
                        mAcceptSocket.getOutputStream(),
                        "501 Not Implemented",
                        "localhost",
                        m_proxySSLEngine.getConnectionDetails().getLocalPort()
                );
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            ProxyEngine.safeClose(mAcceptSocket);
            mAsyncRunner.closed(this);
        }
    }

    public void close() {
        ProxyEngine.safeClose(mAcceptSocket);
    }
}
