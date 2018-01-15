package me.lty.ssltest.mitm;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Describe
 * <p>
 * Created on: 2018/1/15 下午7:32
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class ClientB2ServerA extends StreamThread{
    @Override
    public String getTag(){
        return ClientB2ServerA.class.getSimpleName();
    }

    public ClientB2ServerA(ConnectionDetails connectionDetails, InputStream in, OutputStream out,
                           ProxyDataFilter filter, PrintWriter outputWriter) {
        super(connectionDetails, in, out, filter, outputWriter);
    }
}
