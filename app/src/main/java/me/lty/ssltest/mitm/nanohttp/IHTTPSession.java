package me.lty.ssltest.mitm.nanohttp;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Describe
 * <p>
 * Created on: 2018/1/16 下午5:23
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public abstract class IHTTPSession {



    /**
     * Decode percent encoded <code>String</code> values.
     *
     * @param str the percent encoded <code>String</code>
     * @return expanded form of the input, for example "foo%20bar" becomes
     * "foo bar"
     */
    protected static String decodePercent(String str) {
        String decoded = null;
        try {
            decoded = URLDecoder.decode(str, "UTF8");
        } catch (UnsupportedEncodingException ignored) {
            System.out.println("Encoding not supported");
            ignored.printStackTrace();
        }
        return decoded;
    }
}
