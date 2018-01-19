package me.lty.ssltest;

import android.content.res.AssetManager;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.Security;
import java.security.cert.X509Certificate;

import me.lty.ssltest.mitm.CAConfig;
import me.lty.ssltest.mitm.tool.CertUtil;

/**
 * Describe
 * <p>
 * Created on: 2018/1/16 下午5:53
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class Config {

    public static final String PROXY_SERVER_LISTEN_HOST = "127.0.0.1";
    public static final int PROXY_SERVER_LISTEN_PORT = 9589;

    private static Config mInstance;

    private final CAConfig mCaConfig;

    private Config() throws Exception{
        Security.addProvider(new BouncyCastleProvider());

        AssetManager assets = App.context().getAssets();

        mCaConfig = new CAConfig();

        X509Certificate certificate = CertUtil.loadCert(assets.open("ca.crt"));
        //读取CA证书使用者信息
        mCaConfig.setIssuer(CertUtil.getSubject(certificate));
        //读取CA证书有效时段(server证书有效期超出CA证书的，在手机上会提示证书不安全)
        mCaConfig.setCaNotBefore(certificate.getNotBefore());
        mCaConfig.setCaNotAfter(certificate.getNotAfter());
        //CA私钥用于给动态生成的网站SSL证书签证
        mCaConfig.setCaPriKey(CertUtil.loadPriKey(assets.open(("ca_private.der"))));
        //生产一对随机公私钥用于网站SSL证书动态创建
        KeyPair keyPair = CertUtil.genKeyPair();
        mCaConfig.setServerPriKey(keyPair.getPrivate());
        mCaConfig.setServerPubKey(keyPair.getPublic());
    }

    public static Config getInstance() throws Exception{
        if (mInstance == null){
            synchronized (Config.class){
                mInstance = new Config();
            }
        }
        return mInstance;
    }

    public CAConfig getCaConfig() {
        return mCaConfig;
    }
}
