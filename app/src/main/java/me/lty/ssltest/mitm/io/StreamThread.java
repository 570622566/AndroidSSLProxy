package me.lty.ssltest.mitm.io;// This file is part of The Grinder software distribution. Refer to
// the file LICENSE which is part of The Grinder distribution for
// licensing details. The Grinder distribution is available on the
// Internet at http://grinder.sourceforge.net/

import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import me.lty.ssltest.mitm.ConnectionDetails;

/**
 * Copies bytes from an InputStream to an OutputStream.  Uses a
 * ProxyDataFilter to log the contents appropriately.
 */
public class StreamThread implements Runnable {
    // For simplicity, the filters take a buffer oriented approach.
    // This means that they all break at buffer boundaries. Our buffer
    // is huge, so we shouldn't practically cause a problem, but the
    // network clearly can by giving us message fragments. 
    // We really ought to take a stream oriented approach.
    private final static int BUFFER_SIZE = 65536;

    private static final String TAG = StreamThread.class.getSimpleName();

    private final ConnectionDetails m_connectionDetails;
    private final InputStream m_in;
    private final OutputStream m_out;

    public StreamThread(ConnectionDetails connectionDetails,
                        InputStream in, OutputStream out) {
        m_connectionDetails = connectionDetails;
        m_in = in;
        m_out = out;

        final Thread t = new Thread(
                this,
                "Filter thread for " + m_connectionDetails.getDescription()
        );

        t.start();
    }

    public void run() {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];

            while (true) {
                final int bytesRead = m_in.read(buffer, 0, BUFFER_SIZE);

                if (bytesRead == -1) {
                    break;
                }
                m_out.write(buffer, 0, bytesRead);
            }
        } catch (SocketException e) {
            e.printStackTrace(System.err);
            // Be silent about SocketExceptions.
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        // We're exiting, usually because the in stream has been
        // closed. Whatever, close our streams. This will cause the
        // paired thread to exit to.

        try {
            m_out.close();
            m_in.close();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public String getTAG() {
        return TAG;
    }
}
