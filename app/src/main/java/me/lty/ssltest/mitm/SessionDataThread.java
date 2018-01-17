package me.lty.ssltest.mitm;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import javax.net.ssl.SSLServerSocket;

/**
 * Describe
 * <p>
 * Created on: 2018/1/17 上午11:23
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class SessionDataThread implements Runnable {

    private static final String TAG = SessionDataThread.class.getSimpleName();

    private SSLServerSocket mProxySSLServer;

    public SessionDataThread(SSLServerSocket serverSocket) {
        this.mProxySSLServer = serverSocket;
    }

    @Override
    public void run() {
        try {
            final Socket localSocket = this.mProxySSLServer.accept();

            //解析请求 --- 拦截 --- 发送请求
            InputStream inputStream = localSocket.getInputStream();
            byte[] buffer = new byte[10240];
            int rlen = 0;
            while ((rlen = inputStream.read(buffer)) >= 0) {

            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }
}
