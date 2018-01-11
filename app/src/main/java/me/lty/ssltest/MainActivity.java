package me.lty.ssltest;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;

import me.lty.ssltest.mitm.CAConfig;
import me.lty.ssltest.mitm.CertPool;
import me.lty.ssltest.mitm.tool.CertUtil;
import me.lty.ssltest.mitm.MITMProxyServer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private TextView certStatus;
    private boolean certInstalled;
    private X509Certificate mCert;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        certStatus = findViewById(R.id.textView);
        Button button = findViewById(R.id.button);
        button.setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final int memClass = ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE))
                .getMemoryClass();
        int canUseMemoryCache = 1024 * 1024 * memClass / 8;
        Log.d(TAG, "cache size = " + canUseMemoryCache / 1024 / 1024 + "M");

        certInstalled = false;
        try {
            mCert = CertUtil.loadCert(getAssets().open("ca.crt"));
            certInstalled = CertUtil.isCertInstalled(mCert);
        } catch (Exception e) {
            e.printStackTrace();
        }
        certStatus.setText(certInstalled ? "Status OK" : "Status No Install");

        WifiProxyUtil wifiProxyUtil = WifiProxyUtil.getInstance(this);

        String host = "127.0.0.1";
        int port = 9990;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                wifiProxyUtil.setHttpPorxySetting(host, port, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            wifiProxyUtil.setWifiProxySettingsFor17And(host, port, null);
        }
        startSSLServer();
    }

    @Override
    protected void onDestroy() {
        //TODO 如果应用被强制杀死，App不会执行此方法，解决方案为记录标志，如果非正常退出，再下次启动时提示用户 并清除代理信息的配置

        WifiProxyUtil wifiProxyUtil = WifiProxyUtil.getInstance(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                wifiProxyUtil.unSetHttpProxy();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        } else {
            wifiProxyUtil.unsetWifiProxySettingsFor17And();
        }
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (certInstalled) {
            return;
        }

        byte[] certAsDer = null;
        try {
            certAsDer = this.mCert.getEncoded();
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
        }
        if (certAsDer == null) {
            Log.i(TAG, " certAsDer = this.mCertKeyStore.getCertAsDer(); is null");
            return;
        }
        Intent caIntent = KeyChain.createInstallIntent();
        caIntent.putExtra("name", "CA Certificate");
        caIntent.putExtra("CERT", certAsDer);
        try {
            startActivityForResult(caIntent, 0);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Certificate installer is missing on this device",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0 && resultCode == RESULT_OK) {
            certInstalled = true;
            certStatus.setText("Status OK");
        }
    }

    private void startSSLServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MITMProxyServer server = new MITMProxyServer();
                    server.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void sslTest() throws Exception {
        SSLContext context = createSSLContext("mobile.test.com");
        //SSLContext context = createSSLContext("localhost");

        SSLServerSocket serverSocket = (SSLServerSocket) context
                .getServerSocketFactory()
                .createServerSocket(9990);

        while (true) {
            try {
                Log.d(TAG, "Server Side......");
                Socket connection = serverSocket.accept();
                final InputStream input = connection.getInputStream();
                OutputStream output = connection.getOutputStream();

                InputStreamReader bis = new InputStreamReader(input);
                OutputStreamWriter bos = new OutputStreamWriter(output);
                BufferedReader reader = new BufferedReader(bis);
                BufferedWriter writer = new BufferedWriter(bos);

                String head = reader.readLine();

                String[] split = head.split(" ");
                if (split.length < 3) {
                    connection.close();
                    continue;
                }

                String requestType = split[0];
                String urlString = split[1];
                String httpVersion = split[2];

                URI url = null;
                String host;
                int port;

                if (requestType.equals("CONNECT")) {
                    String[] hostPortSplit = urlString.split(":");
                    host = hostPortSplit[0];
                    // Use default SSL port if not specified. Parse it otherwise
                    if (hostPortSplit.length < 2) {
                        port = 443;
                    } else {
                        try {
                            port = Integer.parseInt(hostPortSplit[1]);
                        } catch (NumberFormatException nfe) {
                            connection.close();
                            continue;
                        }
                    }
                    urlString = "https://" + host + ":" + port;
                    Log.d(TAG, urlString);

                    writer.write("HTTP/1.1 200 Connection established\r\n\r\n");
                    writer.flush();
                } else {
                    String body = "<center>This is Server<center>";

                    writer.write("HTTP/1.1 200 OK\r\n" +
                                         "Server: nginx/1.12.1\r\n" +
                                         "Content-Type: text/html; charset=UTF-8\r\n" +
                                         "Connection: close\r\n" +
                                         "X-Powered-By: PHP/5.6.30\r\n" +
                                         "Content-Length: " + body.length() + "\r\n");
                    writer.write("\r\n\r\n");
                    writer.write(body);
                    writer.flush();
                    connection.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private SSLContext createSSLContext(String host) throws Exception {
        if (TextUtils.isEmpty(host)) {
            return null;
        }
        X509Certificate cert = CertPool.getCert(host, serverConfig);
        X509Certificate[] keyCertChain = new X509Certificate[]{cert};

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("key", serverConfig.getServerPriKey(), null, keyCertChain);

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, null);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
        return context;
    }

    private CAConfig serverConfig;

    private void init() throws Exception {
        //注册BouncyCastleProvider加密库
        Security.addProvider(new BouncyCastleProvider());
        if (serverConfig == null) {
            serverConfig = new CAConfig();

            AssetManager assets = getAssets();
            java.security.cert.X509Certificate certificate = CertUtil.loadCert(assets.open("ca.crt"));
            //读取CA证书使用者信息
            serverConfig.setIssuer(CertUtil.getSubject(certificate));
            //读取CA证书有效时段(server证书有效期超出CA证书的，在手机上会提示证书不安全)
            serverConfig.setCaNotBefore(certificate.getNotBefore());
            serverConfig.setCaNotAfter(certificate.getNotAfter());
            //CA私钥用于给动态生成的网站SSL证书签证
            serverConfig.setCaPriKey(CertUtil.loadPriKey(assets.open("ca_private.der")));
            //生产一对随机公私钥用于网站SSL证书动态创建
            KeyPair keyPair = CertUtil.genKeyPair();
            serverConfig.setServerPriKey(keyPair.getPrivate());
            serverConfig.setServerPubKey(keyPair.getPublic());
        }

    }
}
