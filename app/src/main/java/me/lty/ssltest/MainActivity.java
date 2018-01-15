package me.lty.ssltest;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

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
        button.setOnClickListener(this);
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
        installCert();
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
        String host = "127.0.0.1";
        int port = 9990;
        WifiProxyUtil wifiProxyUtil = WifiProxyUtil.getInstance(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                wifiProxyUtil.setHttpPorxySetting(host, port, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            wifiProxyUtil.setWifiProxySettingsFor17And(host, port, null);
        }
    }
}
