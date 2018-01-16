package me.lty.ssltest.mitm;// This file is part of The Grinder software distribution. Refer to
// the file LICENSE which is part of The Grinder distribution for
// licensing details. The Grinder distribution is available on the
// Internet at http://grinder.sourceforge.net/

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Runnable that actively copies from an InputStream to
 * an OutputStream.
 */
public class ClientAOutCopyStreamRunnable implements Runnable {

    private final String TAG;
    private final Socket socket;
    private final OutputStream m_out;

    public ClientAOutCopyStreamRunnable(Socket clientA , OutputStream out, String tag) {
        socket = clientA;
        m_out = out;
        TAG = tag;
    }

    public void run() {
        final byte[] buffer = new byte[4096];

        try {
            short idle = 0;

            while (true) {
                if (!socket.isConnected()){
                    Log.d("wait ------","wait connected");
                    continue;
                }
                final int bytesRead = socket.getInputStream().read(buffer, 0, buffer.length);

                if (bytesRead == -1) {
                    break;
                }

                if (bytesRead == 0) {
                    idle++;
                } else {
                    final String line = new String(buffer, 0, bytesRead, "US-ASCII");
                    Log.wtf(TAG,line);

                    m_out.write(buffer, 0, bytesRead);
                    idle = 0;
                }

                if (idle > 0) {
                    Thread.sleep(Math.max(idle * 200, 2000));
                }
            }
        } catch (IOException e) {
            // Be silent about IOExceptions ...
            Log.d(TAG,"Got catch ---  1");
            e.printStackTrace();
        } catch (InterruptedException e) {
            // ... and InterruptedExceptions.
            Log.d(TAG,"Got catch ---  1");
            e.printStackTrace();
        }

        // We're exiting, usually because the in stream has been
        // closed. Whatever, close our streams. This will cause the
        // paired thread to exit too.
        try {
            Log.d(TAG,"close our stream");
            m_out.close();
            socket.getInputStream().close();
            socket.close();
        } catch (IOException e) {
            Log.d(TAG,"Got catch ---  1");
            e.printStackTrace();
        }
    }
}
