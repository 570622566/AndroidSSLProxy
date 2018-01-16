//package me.lty.ssltest.mitm.engine;
//
//import android.util.Log;
//
//import java.io.IOException;
//import java.net.ServerSocket;
//import java.net.Socket;
//import java.util.HashMap;
//
//import me.lty.ssltest.mitm.ConnectionDetails;
//import me.lty.ssltest.mitm.ProxyDataFilter;
//import me.lty.ssltest.mitm.factory.MITMSSLSocketFactory;
//import me.lty.ssltest.mitm.factory.MITMSocketFactory;
//
///**
// * Describe
// * Used to funnel data between a client (e.g. a web browser) and a
// * remote SSLServer, that the client is making a request to.
// * <p>
// * Created on: 2018/1/12 上午10:45
// * Email: lty81372860@sina.com
// * <p>
// * Copyright (c) 2018 lty. All rights reserved.
// * Revision：
// *
// * @author lty
// * @version v1.0
// */
//public class ProxySSLEngine extends ProxyEngine {
//
//    private static final String TAG = ProxySSLEngine.class.getSimpleName();
//
//    //NOTE: might be handy to use a bounded size cache..
//    private final HashMap<String, MITMSSLSocketFactory> cnMap = new HashMap<>();
//
//    private Socket remoteSocket = null;
//    int timeout = 0;
//
//    private String m_tempRemoteHost;
//    private int m_tempRemotePort;
//
//    public ProxySSLEngine(MITMSocketFactory socketFactory,
//                          ConnectionDetails connectionDetails,
//                          ProxyDataFilter requestFilter,
//                          ProxyDataFilter responseFilter)
//            throws IOException {
//        super(socketFactory, requestFilter, responseFilter, connectionDetails);
//    }
//
//    public final void setRemoteSocket(Socket s) {
//        this.remoteSocket = s;
//    }
//
//    public void setTempRemote(String remoteHost, int remotePort) {
//        m_tempRemoteHost = remoteHost;
//        m_tempRemotePort = remotePort;
//    }
//
//    public final ServerSocket createServerSocket(String remoteServerCN) throws Exception {
//        assert remoteServerCN != null;
//
//        MITMSSLSocketFactory ssf = null;
//
//        if (cnMap.get(remoteServerCN) == null) {
//            //Instantiate a NEW SSLSocketFactory with a cert that's based on the remote
//            // server's Common Name
//            Log.d(TAG, "[HTTPSProxyEngine] Creating a new certificate for " +
//                    remoteServerCN);
//            ssf = new MITMSSLSocketFactory(remoteServerCN);
//            cnMap.put(remoteServerCN, ssf);
//        } else {
//            Log.d(TAG, "Found cached certificate for " + remoteServerCN);
//            ssf = cnMap.get(remoteServerCN);
//        }
//        m_serverSocket = ssf.createServerSocket(
//                getConnectionDetails().getLocalHost(),
//                0
//        );
//        return m_serverSocket;
//    }
//
//    public final ServerSocket createServerSocket(String remoteServerCN, iaik
//            .x509.X509Certificate remoteServerCert)
//            throws Exception {
//
//        assert remoteServerCN != null;
//
//        MITMSSLSocketFactory ssf = null;
//
//        if (cnMap.get(remoteServerCN) == null) {
//            //Instantiate a NEW SSLSocketFactory with a cert that's based on the remote
//            // server's Common Name
//            Log.d(TAG, "Creating a new certificate for " +
//                    remoteServerCN);
//            ssf = new MITMSSLSocketFactory(remoteServerCN, remoteServerCert);
//            cnMap.put(remoteServerCN, ssf);
//        } else {
//            Log.d(TAG, "Found cached certificate for " + remoteServerCN);
//            ssf = cnMap.get(remoteServerCN);
//        }
//        m_serverSocket = ssf.createServerSocket(
//                getConnectionDetails().getLocalHost(),
//                0
//        );
//        return m_serverSocket;
//    }
//
//    /*
//     * localSocket.get[In|Out]putStream() is data that's (indirectly)
//     * being read from / written to the client.
//     *
//     * m_tempRemoteHost is the remote SSL Server.
//     */
//    public void run() {
//        try {
//            final Socket localSocket = this.getServerSocket().accept();
//
//            Log.e(TAG, "New proxy proxy connection to " + m_tempRemoteHost + ":" + m_tempRemotePort);
//
//            this.launchThreadPair(localSocket, remoteSocket,
//                                  localSocket.getInputStream(),
//                                  localSocket.getOutputStream(),
//                                  m_tempRemoteHost, m_tempRemotePort
//            );
//        } catch (IOException e) {
//            e.printStackTrace(System.err);
//        }
//    }
//}
