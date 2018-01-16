//package me.lty.ssltest.mitm;
//
//import android.util.Log;
//
//import java.io.BufferedInputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.net.ServerSocket;
//import java.net.Socket;
//import java.security.cert.X509Certificate;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//import javax.net.ssl.SSLSocket;
//
//import iaik.asn1.ObjectID;
//import iaik.asn1.structures.Name;
//import me.lty.ssltest.mitm.engine.CopyHTTPSProxyEngine;
//import me.lty.ssltest.mitm.engine.ProxyEngine;
//import me.lty.ssltest.mitm.engine.ProxySSLEngine;
//
///**
// * Describe
// * <p>
// * Created on: 2018/1/13 上午11:38
// * Email: lty81372860@sina.com
// * <p>
// * Copyright (c) 2018 lty. All rights reserved.
// * Revision：
// *
// * @author lty
// * @version v1.0
// */
//public class ConnectionHandler implements Runnable {
//
//    private static final String TAG = ConnectionHandler.class.getSimpleName();
//
//    private AsyncRunner mAsyncRunner;
//    private Socket mAcceptSocket;
//    private CopyHTTPSProxyEngine.InnerProxySSLEngine m_proxySSLEngine;
//    private Pattern m_httpsConnectPattern;
//
//    private String remoteHost;
//    private int remotePort;
//    private final InputStream in;
//
//    public ConnectionHandler(AsyncRunner asyncRunner, Socket connection,InputStream inputStream, CopyHTTPSProxyEngine.InnerProxySSLEngine
//            proxySSLEngine,String remoteHost,int remotePort) {
//        this.mAsyncRunner = asyncRunner;
//        this.mAcceptSocket = connection;
//        this.m_proxySSLEngine = proxySSLEngine;
//
//        m_httpsConnectPattern =
//                Pattern.compile(
//                        "^CONNECT[ \\t]+([^:]+):(\\d+).*\r\n\r\n",
//                        Pattern.DOTALL
//                );
//
//        this.remoteHost = remoteHost;
//        this.remotePort = remotePort;
//
//        in = inputStream;
//    }
//
//    @Override
//    public void run() {
//        String target = remoteHost+":"+remotePort;
//        try{
//            X509Certificate java_cert = null;
//            SSLSocket remoteSocket = null;
//            try {
//                //Lookup the "common name" field of the certificate from the remote server:
//                remoteSocket = (SSLSocket)
//                        m_proxySSLEngine.getSocketFactory()
//                                        .createClientSocket(remoteHost, remotePort);
//                java_cert = (X509Certificate) remoteSocket.getSession()
//                                                          .getPeerCertificates()[0];
//            } catch (IOException ioe) {
//                ioe.printStackTrace();
//                // Try to be nice and send a reasonable error message to client
//                try {
//                    ProxyUtil.sendClientResponse(
//                            mAcceptSocket.getOutputStream(),
//                            "504 Gateway Timeout",
//                            remoteHost,
//                            remotePort
//                    );
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                return;
//            }
//
//            //Use the IAIK X509Cert class, because it has a simple way to get the CN
//            iaik.x509.X509Certificate cert = new iaik.x509.X509Certificate(java_cert.getEncoded());
//            Name n = (Name) cert.getSubjectDN();
//            String serverCN = n.getRDN(ObjectID.commonName);
//
//            Log.d(TAG, "[HTTPSProxyEngine] Remote Server Cert CN= " + serverCN);
//
//            //We've already opened the socket, so might as well keep using it:
//            m_proxySSLEngine.setRemoteSocket(remoteSocket);
//
//            //This is a CRUCIAL step:  we dynamically generate a new cert, based
//            // on the remote server's CN, and return a reference to the internal
//            // server socket that will make use of it.
//            ServerSocket localProxy = m_proxySSLEngine.createServerSocket(serverCN, cert);
//
//            //Kick off a new thread to send/recv data to/from the remote server.
//            // Remote server's response data is made available via an internal
//            // SSLServerSocket.  All this work is handled by the m_proxySSLEngine:
//            new Thread(m_proxySSLEngine, "HTTPS proxy SSL engine").start();
//
//            try {
//                Thread.sleep(10);
//            } catch (Exception ignore) {
//            }
//
//            // Create a new socket connection to our proxy engine.
//            final Socket sslProxySocket =
//                    m_proxySSLEngine.getSocketFactory().createClientSocket(
//                            m_proxySSLEngine.getConnectionDetails().getLocalHost(),
//                            localProxy.getLocalPort()
//                    );
//
//            // Now set up a couple of threads to punt
//            // everything we receive over localSocket to
//            // sslProxySocket, and vice versa.
//            new Thread(
//                    new CopyStreamRunnable(
//                            in,
//                            sslProxySocket.getOutputStream(),
//                            "Copy to proxy engine for " + target
//                    ),
//                    "Copy to proxy engine for " + target
//            ).start();
//
//            final OutputStream out = mAcceptSocket.getOutputStream();
//
//            new Thread(
//                    new CopyStreamRunnable(
//                            sslProxySocket.getInputStream(),
//                            out,
//                            "Copy from proxy engine for " + target
//                    ),
//                    "Copy from proxy engine for " + target
//            ).start();
//
//            // Send a 200 response to send to client. Client
//            // will now start sending SSL data to localSocket.
//            ProxyUtil.sendClientResponse(out, "200 Connection established", remoteHost, remotePort);
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//    }
//
//    public void close() {
//        ProxyUtil.safeClose(mAcceptSocket);
//    }
//}
