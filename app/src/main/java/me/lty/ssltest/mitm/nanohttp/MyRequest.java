package me.lty.ssltest.mitm.nanohttp;

import android.support.v4.util.ArrayMap;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Describe
 * <p>
 * Created on: 2018/1/16 下午5:16
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class MyRequest extends IHTTPSession {

    private static final String TAG = MyRequest.class.getSimpleName();

    /**
     * HTTP MyRequest methods, with the ability to decode a <code>String</code>
     * back to its enum value.
     */
    public enum Method {
        GET,
        PUT,
        POST,
        DELETE,
        HEAD,
        OPTIONS,
        TRACE,
        CONNECT,
        PATCH,
        PROPFIND,
        PROPPATCH,
        MKCOL,
        MOVE,
        COPY,
        LOCK,
        UNLOCK;

        static Method lookup(String method) {
            if (method == null)
                return null;

            try {
                return valueOf(method);
            } catch (IllegalArgumentException e) {
                // TODO: Log it?
                return null;
            }
        }

        static boolean isInstanceOf(Method method) {
            return method == lookup(method.name());
        }
    }

    private int splitbyte;
    private int rlen;

    public final ArrayMap<String, List<String>> parms;
    public final ArrayMap<String, String> headers;
    public final Method method;
    public final String uri;
    public final CookieHandler cookies;
    public String protocolVersion;
    public final boolean keepAlive;

    private String requestBody;

    public MyRequest(byte[] buffer, int bytesRead) throws Exception {
        this.rlen = this.splitbyte = findHeadEnd(buffer, bytesRead);

        this.parms = new ArrayMap<>();
        this.headers = new ArrayMap<>();

        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
                buffer, 0, rlen
        )));
        ArrayMap<String, String> pre = new ArrayMap<>();

        decodeHeader(reader, pre, this.parms, this.headers);

        this.method = Method.lookup(pre.get("method"));
        if (this.method == null) {
            throw new Exception("BAD REQUEST: Syntax error. HTTP verb " + pre.get("method") + " " +
                                        "unhandled.");
        }

        this.uri = pre.get("uri");

        this.cookies = new CookieHandler(this.headers);
        String connection = this.headers.get("connection");
        keepAlive = "HTTP/1.1".equals(protocolVersion) && (connection == null || !connection
                .matches("(?i).*close.*"));

        byte[] bodyBytes = new byte[buffer.length - rlen];
        System.arraycopy(buffer, rlen, bodyBytes, 0, bodyBytes.length);

        String Encoding = "content-encoding";
        if (headers.containsKey(Encoding)) {
            byte[] uncompressBody = null;
            String encodeType = headers.get(Encoding);
            if ("gzip".equals(encodeType)) {
                uncompressBody = gzipUncompress(bodyBytes);
            } else if ("deflate".equals(encodeType) || "inflate".equals(encodeType)) {
                uncompressBody = deflateUncompress(bodyBytes);
            }
            if (uncompressBody != null) {
                try {
                    requestBody = new String(uncompressBody, 0, uncompressBody.length, "UTF-8");
                    Log.wtf("MyRequest", requestBody);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static byte[] gzipUncompress(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        try {
            GZIPInputStream ungzip = new GZIPInputStream(in);
            byte[] buffer = new byte[256];
            int n;
            while ((n = ungzip.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    private byte[] deflateUncompress(byte[] bodyBytes) {
        InflaterInputStream inflaterInputStream = new InflaterInputStream(new ByteArrayInputStream(
                bodyBytes));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[256];
            int n;
            while ((n = inflaterInputStream.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out.toByteArray();
    }

    /**
     * Deduce body length in bytes. Either from "content-length" header or
     * read bytes.
     */
    public int getBodySize() {
        if (this.headers.containsKey("content-length")) {
            return Integer.parseInt(this.headers.get("content-length"));
        } else if (this.splitbyte < this.rlen) {
            return this.rlen - this.splitbyte;
        }
        return 0;
    }

    private int findHeadEnd(byte[] buffer, int bytesRead) {
        int splitbyte = 0;
        while (splitbyte + 1 < bytesRead) {
            // RFC2616
            if (buffer[splitbyte] == '\r' && buffer[splitbyte + 1] == '\n' && splitbyte + 3 <
                    bytesRead && buffer[splitbyte + 2] == '\r' && buffer[splitbyte + 3] == '\n') {
                return splitbyte + 4;
            }
            // tolerance
            if (buffer[splitbyte] == '\n' && buffer[splitbyte + 1] == '\n') {
                return splitbyte + 2;
            }
            splitbyte++;
        }
        return 0;
    }

    /**
     * Decodes the sent headers and loads the data into Key/value pairs
     */
    private void decodeHeader(BufferedReader in, ArrayMap<String, String> pre, ArrayMap<String,
            List<String>> parms, ArrayMap<String, String> headers) throws Exception {
        try {
            // Read the request line
            String inLine = in.readLine();
            if (inLine == null) {
                return;
            }

            StringTokenizer st = new StringTokenizer(inLine);
            if (!st.hasMoreTokens()) {
                throw new Exception(
                        "BAD REQUEST: Syntax error. Usage: GET /example/file.html"
                );
            }

            pre.put("method", st.nextToken());

            if (!st.hasMoreTokens()) {
                throw new Exception(
                        "BAD REQUEST: Missing URI. Usage: GET /example/file.html"
                );
            }

            String uri = st.nextToken();

            // Decode parameters from the URI
            int qmi = uri.indexOf('?');
            if (qmi >= 0) {
                decodeParms(uri.substring(qmi + 1), parms);
                uri = decodePercent(uri.substring(0, qmi));
            } else {
                uri = decodePercent(uri);
            }

            // If there's another token, its protocol version,
            // followed by HTTP headers.
            // NOTE: this now forces header names lower case since they are
            // case insensitive and vary by client.
            if (st.hasMoreTokens()) {
                protocolVersion = st.nextToken();
            } else {
                protocolVersion = "HTTP/1.1";
                Log.d(TAG, "no protocol version specified, strange. Assuming HTTP/1.1.");
            }
            String line = in.readLine();
            while (line != null && !line.trim().isEmpty()) {
                int p = line.indexOf(':');
                if (p >= 0) {
                    headers.put(
                            line.substring(0, p).trim().toLowerCase(Locale.US),
                            line.substring(p + 1).trim()
                    );
                }
                line = in.readLine();
            }

            pre.put("uri", uri);
        } catch (IOException ioe) {
            throw new Exception(
                    "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage(),
                    ioe
            );
        }
    }

    /**
     * Decodes parameters in percent-encoded URI-format ( e.g.
     * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
     * Map.
     */
    private void decodeParms(String parms, Map<String, List<String>> p) {
        if (parms == null) {
            return;
        }

        StringTokenizer st = new StringTokenizer(parms, "&");
        while (st.hasMoreTokens()) {
            String e = st.nextToken();
            int sep = e.indexOf('=');
            String key = null;
            String value = null;

            if (sep >= 0) {
                key = decodePercent(e.substring(0, sep)).trim();
                value = decodePercent(e.substring(sep + 1));
            } else {
                key = decodePercent(e).trim();
                value = "";
            }

            List<String> values = p.get(key);
            if (values == null) {
                values = new ArrayList<>();
                p.put(key, values);
            }

            values.add(value);
        }
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(method.name() + " " + uri + " " + protocolVersion + "\r\n");
        for (String key : headers.keySet()) {
            sb.append(headers.get(key) + "\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }

}
