package me.lty.ssltest.mitm;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Describe
 * <p>
 * Created on: 2018/1/16 上午11:36
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class ProxyUtil {

    private static final String TAG = ProxyUtil.class.getSimpleName();

    public static void sendClientResponse(OutputStream out, String msg, String remoteHost, int
            remotePort) {
        try {
            final StringBuffer response = new StringBuffer();
            response.append("HTTP/1.1 ").append(msg).append("\r\n");
            response.append("Host: " + remoteHost + ":" +
                                    remotePort + "\r\n");
            response.append("Proxy-agent: CS255-MITMProxy/1.0\r\n");
            response.append("\r\n");
            out.write(response.toString().getBytes());
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void safeClose(Object closeable) {
        try {
            if (closeable != null) {
                if (closeable instanceof Closeable) {
                    ((Closeable) closeable).close();
                } else if (closeable instanceof Socket) {
                    ((Socket) closeable).close();
                } else if (closeable instanceof ServerSocket) {
                    ((ServerSocket) closeable).close();
                } else {
                    throw new IllegalArgumentException("Unknown object to close");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not close");
            e.printStackTrace(System.err);
        }
    }
}
