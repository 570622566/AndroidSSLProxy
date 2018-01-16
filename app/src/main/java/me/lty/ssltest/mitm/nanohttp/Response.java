package me.lty.ssltest.mitm.nanohttp;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

import me.lty.ssltest.mitm.engine.ProxyEngine;

import static me.lty.ssltest.mitm.ProxyUtil.safeClose;

class Response implements Closeable {

    private static final String TAG = Response.class.getSimpleName();

    public interface IStatus {

        String getDescription();

        int getRequestStatus();
    }

    /**
     * Some HTTP response status codes
     */
    public enum Status implements IStatus {
        SWITCH_PROTOCOL(101, "Switching Protocols"),

        OK(200, "OK"),
        CREATED(201, "Created"),
        ACCEPTED(202, "Accepted"),
        NO_CONTENT(204, "No Content"),
        PARTIAL_CONTENT(206, "Partial Content"),
        MULTI_STATUS(207, "Multi-Status"),

        REDIRECT(301, "Moved Permanently"),
        /**
         * Many user agents mishandle 302 in ways that violate the RFC1945
         * spec (i.e., redirect a POST to a GET). 303 and 307 were added in
         * RFC2616 to address this. You should prefer 303 and 307 unless the
         * calling user agent does not support 303 and 307 functionality
         */
        @Deprecated
        FOUND(302, "Found"),
        REDIRECT_SEE_OTHER(303, "See Other"),
        NOT_MODIFIED(304, "Not Modified"),
        TEMPORARY_REDIRECT(307, "Temporary Redirect"),

        BAD_REQUEST(400, "Bad Request"),
        UNAUTHORIZED(401, "Unauthorized"),
        FORBIDDEN(403, "Forbidden"),
        NOT_FOUND(404, "Not Found"),
        METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
        NOT_ACCEPTABLE(406, "Not Acceptable"),
        REQUEST_TIMEOUT(408, "Request Timeout"),
        CONFLICT(409, "Conflict"),
        GONE(410, "Gone"),
        LENGTH_REQUIRED(411, "Length Required"),
        PRECONDITION_FAILED(412, "Precondition Failed"),
        PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
        UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
        RANGE_NOT_SATISFIABLE(416, "Requested Range Not Satisfiable"),
        EXPECTATION_FAILED(417, "Expectation Failed"),
        TOO_MANY_REQUESTS(429, "Too Many Requests"),

        INTERNAL_ERROR(500, "Internal Server Error"),
        NOT_IMPLEMENTED(501, "Not Implemented"),
        SERVICE_UNAVAILABLE(503, "Service Unavailable"),
        UNSUPPORTED_HTTP_VERSION(505, "HTTP Version Not Supported");

        private final int requestStatus;

        private final String description;

        Status(int requestStatus, String description) {
            this.requestStatus = requestStatus;
            this.description = description;
        }

        public static Status lookup(int requestStatus) {
            for (Status status : Status.values()) {
                if (status.getRequestStatus() == requestStatus) {
                    return status;
                }
            }
            return null;
        }

        @Override
        public String getDescription() {
            return "" + this.requestStatus + " " + this.description;
        }

        @Override
        public int getRequestStatus() {
            return this.requestStatus;
        }

    }

    /**
     * Output stream that will automatically send every write to the wrapped
     * OutputStream according to chunked transfer:
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1
     */
    private static class ChunkedOutputStream extends FilterOutputStream {

        public ChunkedOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            byte[] data = {
                    (byte) b
            };
            write(data, 0, 1);
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len == 0)
                return;
            out.write(String.format("%x\r\n", len).getBytes());
            out.write(b, off, len);
            out.write("\r\n".getBytes());
        }

        public void finish() throws IOException {
            out.write("0\r\n\r\n".getBytes());
        }

    }

    /**
     * HTTP status code after processing, e.g. "200 OK", Status.OK
     */
    private IStatus status;

    /**
     * MIME type of content, e.g. "text/html"
     */
    private String mimeType;

    /**
     * Data of the response, may be null.
     */
    private InputStream data;

    private long contentLength;

    /**
     * Headers for the HTTP response. Use addHeader() to add lines. the
     * lowercase map is automatically kept up to date.
     */
    @SuppressWarnings("serial")
    private final Map<String, String> header = new HashMap<String, String>() {

        public String put(String key, String value) {
            lowerCaseHeader.put(key == null ? key : key.toLowerCase(), value);
            return super.put(key, value);
        }

        ;
    };

    /**
     * copy of the header map with all the keys lowercase for faster
     * searching.
     */
    private final Map<String, String> lowerCaseHeader = new HashMap<String, String>();

    /**
     * The request method that spawned this response.
     */
    private HttpRequest.Method requestMethod;

    /**
     * Use chunkedTransfer
     */
    private boolean chunkedTransfer;

    private boolean encodeAsGzip;

    private boolean keepAlive;

    /**
     * Creates a fixed length response if totalBytes>=0, otherwise chunked.
     */
    protected Response(IStatus status, String mimeType, InputStream data, long totalBytes) {
        this.status = status;
        this.mimeType = mimeType;
        if (data == null) {
            this.data = new ByteArrayInputStream(new byte[0]);
            this.contentLength = 0L;
        } else {
            this.data = data;
            this.contentLength = totalBytes;
        }
        this.chunkedTransfer = this.contentLength < 0;
        keepAlive = true;
    }

    @Override
    public void close() throws IOException {
        if (this.data != null) {
            this.data.close();
        }
    }

    /**
     * Adds given line to the header.
     */
    public void addHeader(String name, String value) {
        this.header.put(name, value);
    }

    /**
     * Indicate to close the connection after the Response has been sent.
     *
     * @param close {@code true} to hint connection closing, {@code false} to
     *              let connection be closed by client.
     */
    public void closeConnection(boolean close) {
        if (close)
            this.header.put("connection", "close");
        else
            this.header.remove("connection");
    }

    /**
     * @return {@code true} if connection is to be closed after this
     * Response has been sent.
     */
    public boolean isCloseConnection() {
        return "close".equals(getHeader("connection"));
    }

    public InputStream getData() {
        return this.data;
    }

    public String getHeader(String name) {
        return this.lowerCaseHeader.get(name.toLowerCase());
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public HttpRequest.Method getRequestMethod() {
        return this.requestMethod;
    }

    public IStatus getStatus() {
        return this.status;
    }

    public void setGzipEncoding(boolean encodeAsGzip) {
        this.encodeAsGzip = encodeAsGzip;
    }

    public void setKeepAlive(boolean useKeepAlive) {
        this.keepAlive = useKeepAlive;
    }

    /**
     * Sends given response to the socket.
     */
    protected void send(OutputStream outputStream) {
        SimpleDateFormat gmtFrmt = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));

        try {
            if (this.status == null) {
                throw new Error("sendResponse(): Status can't be null.");
            }
            PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    outputStream,
                    new ContentType(this.mimeType).getEncoding()
            )), false);
            pw.append("HTTP/1.1 ").append(this.status.getDescription()).append(" \r\n");
            if (this.mimeType != null) {
                printHeader(pw, "Content-Type", this.mimeType);
            }
            if (getHeader("date") == null) {
                printHeader(pw, "Date", gmtFrmt.format(new Date()));
            }
            for (Map.Entry<String, String> entry : this.header.entrySet()) {
                printHeader(pw, entry.getKey(), entry.getValue());
            }
            if (getHeader("connection") == null) {
                printHeader(pw, "Connection", (this.keepAlive ? "keep-alive" : "close"));
            }
            if (getHeader("content-length") != null) {
                encodeAsGzip = false;
            }
            if (encodeAsGzip) {
                printHeader(pw, "Content-Encoding", "gzip");
                setChunkedTransfer(true);
            }
            long pending = this.data != null ? this.contentLength : 0;
            if (this.requestMethod != HttpRequest.Method.HEAD && this.chunkedTransfer) {
                printHeader(pw, "Transfer-Encoding", "chunked");
            } else if (!encodeAsGzip) {
                pending = sendContentLengthHeaderIfNotAlreadyPresent(pw, pending);
            }
            pw.append("\r\n");
            pw.flush();
            sendBodyWithCorrectTransferAndEncoding(outputStream, pending);
            outputStream.flush();
            safeClose(this.data);
        } catch (IOException ioe) {
            Log.e(TAG, "Could not send response to the client");
            ioe.printStackTrace();
        }
    }

    @SuppressWarnings("static-method")
    protected void printHeader(PrintWriter pw, String key, String value) {
        pw.append(key).append(": ").append(value).append("\r\n");
    }

    protected long sendContentLengthHeaderIfNotAlreadyPresent(PrintWriter pw, long defaultSize) {
        String contentLengthString = getHeader("content-length");
        long size = defaultSize;
        if (contentLengthString != null) {
            try {
                size = Long.parseLong(contentLengthString);
            } catch (NumberFormatException ex) {
                Log.d(TAG,"content-length was no number " + contentLengthString);
                ex.printStackTrace();
            }
        }
        pw.print("Content-Length: " + size + "\r\n");
        return size;
    }

    private void sendBodyWithCorrectTransferAndEncoding(OutputStream outputStream, long pending)
            throws IOException {
        if (this.requestMethod != HttpRequest.Method.HEAD && this.chunkedTransfer) {
            ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(outputStream);
            sendBodyWithCorrectEncoding(chunkedOutputStream, -1);
            chunkedOutputStream.finish();
        } else {
            sendBodyWithCorrectEncoding(outputStream, pending);
        }
    }

    private void sendBodyWithCorrectEncoding(OutputStream outputStream, long pending) throws
            IOException {
        if (encodeAsGzip) {
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
            sendBody(gzipOutputStream, -1);
            gzipOutputStream.finish();
        } else {
            sendBody(outputStream, pending);
        }
    }

    /**
     * Sends the body to the specified OutputStream. The pending parameter
     * limits the maximum amounts of bytes sent unless it is -1, in which
     * case everything is sent.
     *
     * @param outputStream the OutputStream to send data to
     * @param pending      -1 to send everything, otherwise sets a max limit to the
     *                     number of bytes sent
     * @throws IOException if something goes wrong while sending the data.
     */
    private void sendBody(OutputStream outputStream, long pending) throws IOException {
        long BUFFER_SIZE = 16 * 1024;
        byte[] buff = new byte[(int) BUFFER_SIZE];
        boolean sendEverything = pending == -1;
        while (pending > 0 || sendEverything) {
            long bytesToRead = sendEverything ? BUFFER_SIZE : Math.min(pending, BUFFER_SIZE);
            int read = this.data.read(buff, 0, (int) bytesToRead);
            if (read <= 0) {
                break;
            }
            outputStream.write(buff, 0, read);
            if (!sendEverything) {
                pending -= read;
            }
        }
    }

    public void setChunkedTransfer(boolean chunkedTransfer) {
        this.chunkedTransfer = chunkedTransfer;
    }

    public void setData(InputStream data) {
        this.data = data;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setRequestMethod(HttpRequest.Method requestMethod) {
        this.requestMethod = requestMethod;
    }

    public void setStatus(IStatus status) {
        this.status = status;
    }
}