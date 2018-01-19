package me.lty.ssltest.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;

import me.lty.ssltest.Config;
import me.lty.ssltest.service.ProxyService;
import me.lty.ssltest.R;
import me.lty.ssltest.tool.WifiProxyUtil;
import me.lty.ssltest.mitm.tool.CertUtil;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private TextView certStatus;
    private boolean certInstalled;
    private X509Certificate mCert;
    private Intent proxyIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        certStatus = findViewById(R.id.textView);
        Button button = findViewById(R.id.button);
        Button test = findViewById(R.id.test);
        button.setOnClickListener(this);
        test.setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        proxyIntent = new Intent(this, ProxyService.class);

        certInstalled = false;
        try {
            mCert = CertUtil.loadCert(getAssets().open("ca.crt"));
            certInstalled = CertUtil.isCertInstalled(mCert);
        } catch (Exception e) {
            e.printStackTrace();
        }
        certStatus.setText(certInstalled ? "Status OK" : "Status No Install");


        setupWifiProxyConfig();
        startSSLServer();
    }

    @Override
    protected void onDestroy() {
        //TODO 如果应用被强制杀死，App不会执行此方法，解决方案为记录标志，如果非正常退出，再下次启动时提示用户 并清除代理信息的配置

        WifiProxyUtil wifiProxyUtil = WifiProxyUtil.getInstance(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                wifiProxyUtil.unSetHttpProxy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            wifiProxyUtil.unsetWifiProxySettingsFor17And();
        }
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button){
            installCert();
        }else {
            try {
                SSLContext tls = SSLContext.getInstance("TLS");
                tls.init(null,null,new SecureRandom());
                Socket socket = tls.getSocketFactory()
                                   .createSocket(InetAddress.getLocalHost(), 9589);

                String str = "GET / HTTP/1.1\r\n+Host: api.55xiyu.cn\r\n";

                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                writer.write(str);
                writer.flush();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        startService(proxyIntent);
    }

    private void stopSSLServer() {
        stopService(proxyIntent);
    }

    private void installCert() {
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

    private void setupWifiProxyConfig() {
        WifiProxyUtil wifiProxyUtil = WifiProxyUtil.getInstance(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                wifiProxyUtil.setHttpPorxySetting(Config.PROXY_SERVER_LISTEN_HOST, Config.PROXY_SERVER_LISTEN_PORT, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            wifiProxyUtil.setWifiProxySettingsFor17And(Config.PROXY_SERVER_LISTEN_HOST, Config.PROXY_SERVER_LISTEN_PORT, null);
        }
    }
}
