package me.lty.ssltest.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.LruCache;

import org.apache.httpcore.ExceptionLogger;
import org.apache.httpcore.config.SocketConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import javax.net.ServerSocketFactory;

import me.lty.ssltest.Config;
import me.lty.ssltest.mitm.factory.MITMSSLSocketFactory;
import me.lty.ssltest.mitm.flow.DefaultDataFlow;
import me.lty.ssltest.mitm.impl.bootstrap.MITMProxyServer;

/**
 * Describe
 * <p>
 * Created on: 2017/12/25 上午11:52
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2017 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class ProxyService extends Service {

    private ServerThread thread;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle extras = intent.getExtras();
        int listen_port = Config.PROXY_SERVER_LISTEN_PORT;
        if (extras != null) {
            listen_port = extras.getInt("mListenPort");
        }
        if (thread == null) {
            thread = new ServerThread(listen_port);
            thread.start();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        thread.interrupt();
    }

    class ServerThread extends Thread {

        private final int mListenPort;

        public ServerThread(int port) {
            mListenPort = port;
        }

        @Override
        public void run() {
            try {
                MITMProxyServer server = new MITMProxyServer.Builder()
                        .setPort(mListenPort)
                        .setTimeOut(30, TimeUnit.SECONDS)
                        .setSslFactoryCache(new LruCache<String, MITMSSLSocketFactory>(100))
                        .setExceptionLogger(ExceptionLogger.STD_ERR)
                        .build();
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
