package me.lty.ssltest.mitm;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import javax.net.ssl.SSLServerSocket;

import me.lty.ssltest.mitm.nanohttp.MyRequest;

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

            while (true){
                byte[] buffer = new byte[10240];
                int rlen = inputStream.read(buffer,0,10240);
                if (rlen == -1){
                    break;
                }
                try {
                    MyRequest myRequest = new MyRequest(buffer, rlen);
                    Log.d("Http Session", myRequest.toString());

                    MyResponse response = OkHttpUtil.getInstance().doRequest(myRequest);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }
}
