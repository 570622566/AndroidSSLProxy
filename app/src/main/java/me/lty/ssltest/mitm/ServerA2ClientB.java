package me.lty.ssltest.mitm;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import me.lty.ssltest.mitm.filter.ProxyDataFilter;

/**
 * Describe
 * <p>
 * Created on: 2018/1/15 下午7:15
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class ServerA2ClientB extends StreamThread {

    @Override
    public String getTAG() {
        return ServerA2ClientB.class.getSimpleName();
    }

    public ServerA2ClientB(ConnectionDetails connectionDetails, InputStream in, OutputStream out,
                           ProxyDataFilter filter, PrintWriter outputWriter) {
        super(connectionDetails, in, out, filter, outputWriter);
    }
}
