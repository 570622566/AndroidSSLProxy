package me.lty.ssltest;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import me.lty.ssltest.mitm.MITMProxyServer;

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
        thread = new ServerThread();
        thread.start();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        thread.interrupt();
    }

    class ServerThread extends Thread {
        @Override
        public void run() {
            try {
                MITMProxyServer server = new MITMProxyServer();
                server.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
