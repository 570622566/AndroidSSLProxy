package me.lty.ssltest.mitm.engine;
//Based on HTTPProxySnifferEngine.java from The Grinder distribution.
// The Grinder distribution is available at http://grinder.sourceforge.net/
/*
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

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import me.lty.ssltest.mitm.ConnectionDetails;
import me.lty.ssltest.mitm.ProxyDataFilter;
import me.lty.ssltest.mitm.StreamThread;
import me.lty.ssltest.mitm.factory.MITMSocketFactory;


public abstract class ProxyEngine implements Runnable {

    private static final String TAG = ProxyEngine.class.getSimpleName();

    public static final String ACCEPT_TIMEOUT_MESSAGE = "Listen time out";
    private final ProxyDataFilter m_requestFilter;
    private final ProxyDataFilter m_responseFilter;
    private final ConnectionDetails m_connectionDetails;

    private final PrintWriter m_outputWriter;

    public final MITMSocketFactory m_socketFactory;
    protected ServerSocket m_serverSocket;

    public ProxyEngine(MITMSocketFactory socketFactory,
                       ProxyDataFilter requestFilter,
                       ProxyDataFilter responseFilter,
                       ConnectionDetails connectionDetails)
            throws IOException {
        m_socketFactory = socketFactory;
        m_requestFilter = requestFilter;
        m_responseFilter = responseFilter;
        m_connectionDetails = connectionDetails;

        m_outputWriter = requestFilter.getOutputPrintWriter();

        m_serverSocket =
                m_socketFactory.createServerSocket(
                        connectionDetails.getLocalHost(),
                        connectionDetails.getLocalPort()
                );
    }

    //run() method from Runnable is implemented in subclasses

    public final ServerSocket getServerSocket() {
        return m_serverSocket;
    }

    public final MITMSocketFactory getSocketFactory() {
        return m_socketFactory;
    }

    public final ConnectionDetails getConnectionDetails() {
        return m_connectionDetails;
    }


    /*
     * Launch a pair of threads that:
     * (1) Copy data sent from the client to the remote server
     * (2) Copy data sent from the remote server to the client
     *
     */
    public final void launchThreadPair(Socket localSocket, Socket remoteSocket,
                                          InputStream localInputStream,
                                          OutputStream localOutputStream,
                                          String remoteHost,
                                          int remotePort) throws IOException {
        new StreamThread(
                new ConnectionDetails(
                        m_connectionDetails.getLocalHost(),
                        localSocket.getPort(),
                        remoteHost,
                        remoteSocket.getPort(),
                        m_connectionDetails.isSecure()
                ),
                localInputStream,
                remoteSocket.getOutputStream(),
                m_requestFilter,
                m_outputWriter
        );

        new StreamThread(
                new ConnectionDetails(
                        remoteHost,
                        remoteSocket.getPort(),
                        m_connectionDetails.getLocalHost(),
                        localSocket.getPort(),
                        m_connectionDetails.isSecure()
                ),
                remoteSocket.getInputStream(),
                localOutputStream,
                m_responseFilter,
                m_outputWriter
        );
    }

    public static void safeClose(Object closeable) {
        try {
            if (closeable != null) {
                if (closeable instanceof Closeable) {
                    ((Closeable) closeable).close();
                } else if (closeable instanceof Socket) {
                    ((Socket) closeable).close();
                } else if (closeable instanceof ServerSocket) {
                    ((ServerSocket) closeable).close();
                } else {
                    throw new IllegalArgumentException("Unknown object to close");
                }
            }
        } catch (IOException e) {
            Log.e(TAG,"Could not close");
            e.printStackTrace(System.err);
        }
    }

    public static void sendClientResponse(OutputStream out, String msg, String remoteHost, int
            remotePort) throws IOException {
        final StringBuffer response = new StringBuffer();
        response.append("HTTP/1.1 ").append(msg).append("\r\n");
        response.append("Host: " + remoteHost + ":" + remotePort + "\r\n");
        response.append("Proxy-agent: CS255-MITMProxy/1.0\r\n");
        response.append("\r\n");
        out.write(response.toString().getBytes());
        out.flush();
    }
}

