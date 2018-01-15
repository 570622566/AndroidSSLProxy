package me.lty.ssltest.mitm.nanohttp;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLException;

import static me.lty.ssltest.mitm.engine.ProxyEngine.safeClose;

/**
 * Describe
 * <p>
 * Created on: 2018/1/13 下午3:23
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class HttpRequest {

    private static final String TAG = HttpRequest.class.getSimpleName();

    private static final int REQUEST_BUFFER_LEN = 512;

    private static final int MEMORY_STORE_LIMIT = 1024;

    public static final int BUFFER_SIZE = 40960;

    public static final int MAX_HEADER_SIZE = 1024;

    /**
     * HTTP Request methods, with the ability to decode a <code>String</code>
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

    private InputStream inputStream;

    private int splitbyte;

    private int rlen;

    private String uri;

    private Method method;

    private Map<String, List<String>> parms;

    private Map<String, String> headers;

    private CookieHandler cookies;

    private String queryParameterString;

    private String remoteIp;

    private String remoteHost;

    private int remotePort;

    private String protocolVersion;

    Pattern CONTENT_DISPOSITION_PATTERN = Pattern.compile(
            "([ |\t]*Content-Disposition[ |\t]*:)(.*)",
            Pattern.CASE_INSENSITIVE
    );

    Pattern CONTENT_DISPOSITION_ATTRIBUTE_PATTERN = Pattern.compile(
            "[ |\t]*([a-zA-Z]*)[ |\t]*=[ |\t]*['|\"]([^\"^']*)['|\"]");

    Pattern CONTENT_TYPE_PATTERN = Pattern.compile(
            "([ |\t]*content-type[ |\t]*:)(.*)",
            Pattern.CASE_INSENSITIVE
    );

    public HttpRequest(InputStream inputStream) throws Exception {
        this.inputStream = inputStream;
        this.headers = new HashMap<>();
        execute();
    }

    /**
     * Decodes the sent headers and loads the data into Key/value pairs
     */
    private void decodeHeader(BufferedReader in, Map<String, String> pre, Map<String,
            List<String>> parms, Map<String, String> headers) throws Exception {
        try {
            // Read the request line
            String inLine = in.readLine();
            if (inLine == null) {
                return;
            }

            StringTokenizer st = new StringTokenizer(inLine);
            if (!st.hasMoreTokens()) {
                throw new ResponseException(
                        Response.Status.BAD_REQUEST,
                        "BAD REQUEST: Syntax error. Usage: GET /example/file.html"
                );
            }

            String method = st.nextToken();
            pre.put("method", method);

            if (!st.hasMoreTokens()) {
                throw new ResponseException(
                        Response.Status.BAD_REQUEST,
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
            pre.put("uri", uri);

            if ("CONNECT".equals(method)) {
                String[] hostPortSplit = uri.split(":");
                remoteHost = hostPortSplit[0];
                // Use default SSL port if not specified. Parse it otherwise
                if (hostPortSplit.length < 2) {
                    remotePort = 443;
                } else {
                    try {
                        remotePort = Integer.parseInt(hostPortSplit[1]);
                    } catch (NumberFormatException nfe) {
                        throw nfe;
                    }
                }
            } else {
                try {
                    URI url = new URI(uri);
                    remoteHost = url.getHost();
                    remotePort = url.getPort();
                    if (remotePort < 0) {
                        remotePort = 80;
                    }
                } catch (URISyntaxException e) {
                    throw e;
                }
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

        } catch (IOException ioe) {
            throw new ResponseException(
                    Response.Status.INTERNAL_ERROR,
                    "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage(),
                    ioe
            );
        }
    }

    /**
     * Decodes the Multipart Body data and put it into Key/Value pairs.
     */
    private void decodeMultipartFormData(ContentType contentType, ByteBuffer fbuf, Map<String,
            List<String>> parms, Map<String, String> files) throws ResponseException {
        int pcount = 0;
        try {
            int[] boundaryIdxs = getBoundaryPositions(fbuf, contentType.getBoundary().getBytes());
            if (boundaryIdxs.length < 2) {
                throw new ResponseException(
                        Response.Status.BAD_REQUEST,
                        "BAD REQUEST: Content type is multipart/form-data but contains less than " +
                                "two boundary strings."
                );
            }

            byte[] partHeaderBuff = new byte[MAX_HEADER_SIZE];
            for (int boundaryIdx = 0; boundaryIdx < boundaryIdxs.length - 1; boundaryIdx++) {
                fbuf.position(boundaryIdxs[boundaryIdx]);
                int len = (fbuf.remaining() < MAX_HEADER_SIZE) ? fbuf.remaining() : MAX_HEADER_SIZE;
                fbuf.get(partHeaderBuff, 0, len);
                BufferedReader in =
                        new BufferedReader(new InputStreamReader(
                                new ByteArrayInputStream(partHeaderBuff, 0, len),
                                Charset.forName(contentType.getEncoding())
                        ), len);

                int headerLines = 0;
                // First line is boundary string
                String mpline = in.readLine();
                headerLines++;
                if (mpline == null || !mpline.contains(contentType.getBoundary())) {
                    throw new ResponseException(
                            Response.Status.BAD_REQUEST,
                            "BAD REQUEST: Content type is multipart/form-data but chunk does not " +
                                    "start with boundary."
                    );
                }

                String partName = null, fileName = null, partContentType = null;
                // Parse the reset of the header lines
                mpline = in.readLine();
                headerLines++;
                while (mpline != null && mpline.trim().length() > 0) {
                    Matcher matcher = CONTENT_DISPOSITION_PATTERN.matcher(mpline);
                    if (matcher.matches()) {
                        String attributeString = matcher.group(2);
                        matcher = CONTENT_DISPOSITION_ATTRIBUTE_PATTERN.matcher(attributeString);
                        while (matcher.find()) {
                            String key = matcher.group(1);
                            if ("name".equalsIgnoreCase(key)) {
                                partName = matcher.group(2);
                            } else if ("filename".equalsIgnoreCase(key)) {
                                fileName = matcher.group(2);
                                // add these two line to support multiple
                                // files uploaded using the same field Id
                                if (!fileName.isEmpty()) {
                                    if (pcount > 0)
                                        partName = partName + String.valueOf(pcount++);
                                    else
                                        pcount++;
                                }
                            }
                        }
                    }
                    matcher = CONTENT_TYPE_PATTERN.matcher(mpline);
                    if (matcher.matches()) {
                        partContentType = matcher.group(2).trim();
                    }
                    mpline = in.readLine();
                    headerLines++;
                }
                int partHeaderLength = 0;
                while (headerLines-- > 0) {
                    partHeaderLength = scipOverNewLine(partHeaderBuff, partHeaderLength);
                }
                // Read the part data
                if (partHeaderLength >= len - 4) {
                    throw new ResponseException(
                            Response.Status.INTERNAL_ERROR,
                            "Multipart header size exceeds MAX_HEADER_SIZE."
                    );
                }
                int partDataStart = boundaryIdxs[boundaryIdx] + partHeaderLength;
                int partDataEnd = boundaryIdxs[boundaryIdx + 1] - 4;

                fbuf.position(partDataStart);

                List<String> values = parms.get(partName);
                if (values == null) {
                    values = new ArrayList<>();
                    parms.put(partName, values);
                }

                if (partContentType == null) {
                    // Read the part into a string
                    byte[] data_bytes = new byte[partDataEnd - partDataStart];
                    fbuf.get(data_bytes);

                    values.add(new String(data_bytes, contentType.getEncoding()));
                } else {
                    //// Read it into a file
                    //String path = saveTmpFile(
                    //        fbuf,
                    //        partDataStart,
                    //        partDataEnd - partDataStart,
                    //        fileName
                    //);
                    //if (!files.containsKey(partName)) {
                    //    files.put(partName, path);
                    //} else {
                    //    int count = 2;
                    //    while (files.containsKey(partName + count)) {
                    //        count++;
                    //    }
                    //    files.put(partName + count, path);
                    //}
                    //values.add(fileName);
                }
            }
        } catch (ResponseException re) {
            throw re;
        } catch (Exception e) {
            throw new ResponseException(Response.Status.INTERNAL_ERROR, e.toString());
        }
    }

    private int scipOverNewLine(byte[] partHeaderBuff, int index) {
        while (partHeaderBuff[index] != '\n') {
            index++;
        }
        return ++index;
    }

    /**
     * Decodes parameters in percent-encoded URI-format ( e.g.
     * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
     * Map.
     */
    private void decodeParms(String parms, Map<String, List<String>> p) {
        if (parms == null) {
            this.queryParameterString = "";
            return;
        }

        this.queryParameterString = parms;
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

    public void execute() throws Exception {

        // Read the first 8192 bytes.
        // The full header should fit in here.
        // Apache's default header limit is 8KB.
        // Do NOT assume that a single read will get the entire header
        // at once!
        byte[] buf = new byte[BUFFER_SIZE];
        this.splitbyte = 0;

        this.inputStream.mark(BUFFER_SIZE);

        int read = -1;
        try {
            read = this.inputStream.read(buf);
        } catch (SSLException e) {
            throw e;
        } catch (IOException e) {
            safeClose(this.inputStream);
            throw new SocketException("NanoHttpd Shutdown");
        }
        if (read == -1) {
            // socket was been closed
            safeClose(this.inputStream);
            throw new SocketException("NanoHttpd Shutdown");
        }
        this.rlen = read;
        this.splitbyte = findHeaderEnd(buf, read);

        this.parms = new HashMap<>();
        if (null == this.headers) {
            this.headers = new HashMap<>();
        } else {
            this.headers.clear();
        }

        // Create a BufferedReader for parsing the header.
        BufferedReader hin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
                buf,
                0,
                this.splitbyte
        )));

        // Decode the header into parms and header java properties
        Map<String, String> pre = new HashMap<>();
        decodeHeader(hin, pre, this.parms, this.headers);

        if (null != this.remoteIp) {
            this.headers.put("remote-addr", this.remoteIp);
            this.headers.put("http-client-ip", this.remoteIp);
        }

        this.method = Method.lookup(pre.get("method"));
        if (this.method == null) {
            throw new ResponseException(
                    Response.Status.BAD_REQUEST,
                    "BAD REQUEST: Syntax error. HTTP verb " + pre.get("method") + " unhandled."
            );
        }

        this.uri = pre.get("uri");

        this.cookies = new CookieHandler(this.headers);

        String connection = this.headers.get("connection");
        boolean keepAlive = "HTTP/1.1".equals(protocolVersion) && (connection == null ||
                !connection.matches("(?i).*close.*"));
    }

    /**
     * Find byte index separating header from body. It must be the last byte
     * of the first two sequential new lines.
     */
    private int findHeaderEnd(final byte[] buf, int rlen) {
        int splitbyte = 0;
        while (splitbyte + 1 < rlen) {

            // RFC2616
            if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n' && splitbyte + 3 < rlen &&
                    buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n') {
                return splitbyte + 4;
            }

            // tolerance
            if (buf[splitbyte] == '\n' && buf[splitbyte + 1] == '\n') {
                return splitbyte + 2;
            }
            splitbyte++;
        }
        return 0;
    }

    /**
     * Find the byte positions where multipart boundaries start. This reads
     * a large block at a time and uses a temporary buffer to optimize
     * (memory mapped) file access.
     */
    private int[] getBoundaryPositions(ByteBuffer b, byte[] boundary) {
        int[] res = new int[0];
        if (b.remaining() < boundary.length) {
            return res;
        }

        int search_window_pos = 0;
        byte[] search_window = new byte[4 * 1024 + boundary.length];

        int first_fill = (b.remaining() < search_window.length) ? b.remaining() : search_window
                .length;
        b.get(search_window, 0, first_fill);
        int new_bytes = first_fill - boundary.length;

        do {
            // Search the search_window
            for (int j = 0; j < new_bytes; j++) {
                for (int i = 0; i < boundary.length; i++) {
                    if (search_window[j + i] != boundary[i])
                        break;
                    if (i == boundary.length - 1) {
                        // Match found, add it to results
                        int[] new_res = new int[res.length + 1];
                        System.arraycopy(res, 0, new_res, 0, res.length);
                        new_res[res.length] = search_window_pos + j;
                        res = new_res;
                    }
                }
            }
            search_window_pos += new_bytes;

            // Copy the end of the buffer to the start
            System.arraycopy(
                    search_window,
                    search_window.length - boundary.length,
                    search_window,
                    0,
                    boundary.length
            );

            // Refill search_window
            new_bytes = search_window.length - boundary.length;
            new_bytes = (b.remaining() < new_bytes) ? b.remaining() : new_bytes;
            b.get(search_window, boundary.length, new_bytes);
        } while (new_bytes > 0);
        return res;
    }

    public CookieHandler getCookies() {
        return this.cookies;
    }

    public final Map<String, String> getHeaders() {
        return this.headers;
    }

    public final InputStream getInputStream() {
        return this.inputStream;
    }

    public final Method getMethod() {
        return this.method;
    }

    /**
     * @deprecated use {@link #getParameters()} instead.
     */
    @Deprecated
    public final Map<String, String> getParms() {
        Map<String, String> result = new HashMap<>();
        for (String key : this.parms.keySet()) {
            result.put(key, this.parms.get(key).get(0));
        }

        return result;
    }

    public final Map<String, List<String>> getParameters() {
        return this.parms;
    }

    public String getQueryParameterString() {
        return this.queryParameterString;
    }

    public final String getUri() {
        return this.uri;
    }

    /**
     * Deduce body length in bytes. Either from "content-length" header or
     * read bytes.
     */
    public long getBodySize() {
        if (this.headers.containsKey("content-length")) {
            return Long.parseLong(this.headers.get("content-length"));
        } else if (this.splitbyte < this.rlen) {
            return this.rlen - this.splitbyte;
        }
        return 0;
    }

    public String getRemoteIpAddress() {
        return this.remoteIp;
    }

    public String getRemoteHost() {
        return this.remoteHost;
    }

    public int getRemotePort() {
        return remotePort;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(method + " [Request URI] " + uri + " --- " + remoteHost + ":" + remotePort + " " +
                          "--- \r\n");
        sb.append("[Cookies] " + cookies + "\r\n");
        sb.append("[Headers] " + headers.toString() + "\r\n");
        sb.append("[Parameter] " + parms.toString() + "\r\n");
        sb.append("[QueryParameter] " + queryParameterString + "\r\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------------
    // //

    /**
     * Decode parameters from a URL, handing the case where a single parameter
     * name might have been supplied several times, by return lists of values.
     * In general these lists will contain a single element.
     *
     * @param queryString a query string pulled from the URL.
     * @return a map of <code>String</code> (parameter name) to
     * <code>List&lt;String&gt;</code> (a list of the values supplied).
     */
    protected static Map<String, List<String>> decodeParameters(String queryString) {
        Map<String, List<String>> parms = new HashMap<String, List<String>>();
        if (queryString != null) {
            StringTokenizer st = new StringTokenizer(queryString, "&");
            while (st.hasMoreTokens()) {
                String e = st.nextToken();
                int sep = e.indexOf('=');
                String propertyName = sep >= 0 ? decodePercent(e.substring(
                        0,
                        sep
                )).trim() : decodePercent(e).trim();
                if (!parms.containsKey(propertyName)) {
                    parms.put(propertyName, new ArrayList<String>());
                }
                String propertyValue = sep >= 0 ? decodePercent(e.substring(sep + 1)) : null;
                if (propertyValue != null) {
                    parms.get(propertyName).add(propertyValue);
                }
            }
        }
        return parms;
    }

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
            Log.e(TAG, "Encoding not supported, ignored", ignored);
        }
        return decoded;
    }
}
