package me.lty.ssltest.mitm.factory;//Based on SnifferSSLSocketFactory.java from The Grinder
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

import android.content.res.AssetManager;
import android.util.Log;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import me.lty.ssltest.App;
import me.lty.ssltest.mitm.CAConfig;
import me.lty.ssltest.mitm.CertPool;
import me.lty.ssltest.mitm.tool.CertUtil;
import me.lty.ssltest.mitm.tool.SignCert;


/**
 * MITMSSLSocketFactory is used to create SSL sockets.
 * <p>
 * This is needed because the javax.net.ssl socket factory classes don't
 * allow creation of factories with custom parameters.
 */
public final class MITMSSLSocketFactory implements MITMSocketFactory {
    private ServerSocketFactory m_serverSocketFactory;
    private SocketFactory m_clientSocketFactory;
    private SSLContext m_sslContext;

    public KeyStore ks = null;

    private CAConfig caConfig;

    /*
     *
     * We can't install our own TrustManagerFactory without messing
     * with the security properties file. Hence we create our own
     * SSLContext and initialise it. Passing null as the keystore
     * parameter to SSLContext.init() results in a empty keystore
     * being used, as does passing the key manager array obtain from
     * keyManagerFactory.getInstance().getKeyManagers(). To pick up
     * the "default" keystore system properties, we have to read them
     * explicitly. UGLY, but necessary so we understand the expected
     * properties.
     *
     */

    /**
     * This constructor will create an SSL server socket factory
     * that is initialized with a fixed CA certificate
     */
    public MITMSSLSocketFactory() throws Exception {
        init(null, null);
    }

    /**
     * This constructor will create an SSL server socket factory
     * that is initialized with a forged server certificate
     * that is issued by the proxy "CA certificate".
     */
    public MITMSSLSocketFactory(String remoteCN)
            throws Exception {
        init(remoteCN, null);
    }

    public MITMSSLSocketFactory(String remoteCN, iaik.x509.X509Certificate remoteServerCert)
            throws Exception {
        init(remoteCN, remoteServerCert);
    }

    private void init(String remoteCN, iaik.x509.X509Certificate remoteServerCert) throws
            Exception {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new iaik.security.provider.IAIK());

        AssetManager assets = App.context().getAssets();
        if (caConfig == null) {
            caConfig = new CAConfig();

            X509Certificate certificate = CertUtil.loadCert(assets.open("ca.crt"));
            //读取CA证书使用者信息
            caConfig.setIssuer(CertUtil.getSubject(certificate));
            //读取CA证书有效时段(server证书有效期超出CA证书的，在手机上会提示证书不安全)
            caConfig.setCaNotBefore(certificate.getNotBefore());
            caConfig.setCaNotAfter(certificate.getNotAfter());
            //CA私钥用于给动态生成的网站SSL证书签证
            caConfig.setCaPriKey(CertUtil.loadPriKey(assets.open(("ca_private.der"))));
            //生产一对随机公私钥用于网站SSL证书动态创建
            KeyPair keyPair = CertUtil.genKeyPair();
            caConfig.setServerPriKey(keyPair.getPrivate());
            caConfig.setServerPubKey(keyPair.getPublic());
        }

        m_sslContext = SSLContext.getInstance("TLS");

        final KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        if (remoteCN != null) {
            if (remoteServerCert != null) {
                PrivateKey privateKey = CertUtil.loadPriKey(assets.open(("ca_private.der")));
                iaik.x509.X509Certificate cert = CertUtil.loadCert1(assets.open("ca.crt"));
                char[] password = new char[0];
                iaik.x509.X509Certificate newCert = SignCert.forgeCert(
                        privateKey,
                        cert,
                        remoteCN,
                        remoteServerCert
                );
                KeyStore newKS = KeyStore.getInstance(KeyStore.getDefaultType());
                newKS.load(null, null);
                newKS.setKeyEntry(
                        DEFAULT_ALIAS,
                        privateKey,
                        new char[0],
                        new Certificate[]{newCert}
                );
                keyManagerFactory.init(newKS, password);
            } else {
                X509Certificate cert = CertPool.getCert(remoteCN, caConfig);
                X509Certificate[] keyCertChain = new X509Certificate[]{cert};
                keyStore.load(null, null);
                keyStore.setKeyEntry("key", caConfig.getServerPriKey(), null, keyCertChain);
                keyManagerFactory.init(keyStore, null);
            }
            m_sslContext.init(
                    keyManagerFactory.getKeyManagers(),
                    new TrustManager[]{new TrustEveryone()},
                    new SecureRandom()
            );
        } else {
            m_sslContext.init(
                    null,
                    new TrustManager[]{new TrustEveryone()},
                    new SecureRandom()
            );
        }
        m_clientSocketFactory = m_sslContext.getSocketFactory();
        m_serverSocketFactory = m_sslContext.getServerSocketFactory();
    }

    public final ServerSocket createServerSocket(String localHost, int localPort) throws
            IOException {
        final SSLServerSocket socket =
                (SSLServerSocket) m_serverSocketFactory.createServerSocket(
                        localPort, 50, InetAddress.getByName(localHost));
        socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
        return socket;
    }

    public final Socket createClientSocket(String remoteHost, int remotePort)
            throws IOException {
        final SSLSocket socket =
                (SSLSocket) m_clientSocketFactory.createSocket(
                        remoteHost,
                        remotePort
                );
        socket.setEnabledCipherSuites(socket.getSupportedCipherSuites());
        //socket.setSoTimeout(15000);
        socket.startHandshake();
        return socket;
    }

    /**
     * We're carrying out a MITM attack, we don't care whether the cert
     * chains are trusted or not ;-)
     */
    private static class TrustEveryone implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain,
                                       String authenticationType) {
        }

        public void checkServerTrusted(X509Certificate[] chain,
                                       String authenticationType) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}
    
