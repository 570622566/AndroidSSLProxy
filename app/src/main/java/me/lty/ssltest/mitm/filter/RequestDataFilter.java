package me.lty.ssltest.mitm.filter;

import android.util.Log;

import java.io.IOException;

import me.lty.ssltest.mitm.ConnectionDetails;
import me.lty.ssltest.mitm.nanohttp.Request;

/**
 * Describe
 * <p>
 * Created on: 2018/1/16 下午7:32
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class RequestDataFilter extends ProxyDataFilter{
    @Override
    public void handle(String tag, ConnectionDetails connectionDetails, byte[] buffer, int
            bytesRead) throws IOException {
        super.handle(tag, connectionDetails, buffer, bytesRead);
        try {
            Request request = new Request(buffer, bytesRead);
            Log.wtf("Http Session", request.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
