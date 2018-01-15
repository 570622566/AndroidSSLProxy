package me.lty.ssltest.mitm.engine;
//Based on HTTPProxySnifferEngine.java from The Grinder
// distribution.
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

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.regex.PatternSyntaxException;

import me.lty.ssltest.mitm.ConnectionDetails;
import me.lty.ssltest.mitm.ConnectionHandler;
import me.lty.ssltest.mitm.DefaultAsyncRunner;
import me.lty.ssltest.mitm.ProxyDataFilter;
import me.lty.ssltest.mitm.factory.MITMPlainSocketFactory;
import me.lty.ssltest.mitm.factory.MITMSSLSocketFactory;


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
public class HTTPSProxyEngine extends ProxyEngine {

    public static final String ACCEPT_TIMEOUT_MESSAGE = "Listen time out";

    private final ProxySSLEngine m_proxySSLEngine;

    private int timeout = 0;
    private final DefaultAsyncRunner asyncRunner;

    public HTTPSProxyEngine(MITMPlainSocketFactory plainSocketFactory,
                            MITMSSLSocketFactory sslSocketFactory,
                            ProxyDataFilter requestFilter,
                            ProxyDataFilter responseFilter,
                            String localHost,
                            int localPort)
            throws IOException, PatternSyntaxException {
        // We set this engine up for handling plain HTTP and delegate
        // to a proxy for HTTPS.
        super(
                plainSocketFactory,
                requestFilter,
                responseFilter,
                new ConnectionDetails(localHost, localPort, "", -1, false)
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

        ConnectionDetails connectionDetails = new ConnectionDetails(getConnectionDetails().getLocalHost(),
                                                                    0, "", -1, true
        );

        m_proxySSLEngine = new ProxySSLEngine(
                sslSocketFactory,
                connectionDetails,
                requestFilter,
                responseFilter
        );

        asyncRunner = new DefaultAsyncRunner();
    }

    private ConnectionHandler createConnHandler(Socket socket) {
        return new ConnectionHandler(asyncRunner, socket, m_proxySSLEngine);
    }

    public void run() {
        while (true) {
            try {
                //Plaintext Socket with client (i.e. browser)
                final Socket acceptSocket = getServerSocket().accept();
                asyncRunner.exec(createConnHandler(acceptSocket));
            } catch (InterruptedIOException e) {
                e.printStackTrace();
                System.err.println(ACCEPT_TIMEOUT_MESSAGE);
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
