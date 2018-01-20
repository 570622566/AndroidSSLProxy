package me.lty.ssltest.mitm.io;// This file is part of The Grinder software distribution. Refer to
// the file LICENSE which is part of The Grinder distribution for
// licensing details. The Grinder distribution is available on the
// Internet at http://grinder.sourceforge.net/

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Runnable that actively copies from an InputStream to
 * an OutputStream.
 */
public class CopyStreamRunnable implements Runnable {

    private final String TAG;

    private final Socket in;
    private final Socket out;
    private final InputStream m_in;
    private final OutputStream m_out;

    public CopyStreamRunnable(Socket in, Socket out, String tag) throws IOException{
        this.in = in;
        this.out = out;
        m_in = in.getInputStream();
        m_out = out.getOutputStream();
        TAG = tag;
    }

    public void run() {
        final byte[] buffer = new byte[4096];

        try {
            short idle = 0;

            while (!this.in.isClosed() && !this.out.isClosed()) {
                final int bytesRead = m_in.read(buffer, 0, buffer.length);

                if(bytesRead == -1){
                    Log.e("LLYH","copy stream read end");
                    break;
                }

                if (bytesRead == 0) {
                    idle++;
                } else if (bytesRead > 0){
                    m_out.write(buffer, 0, bytesRead);
                    idle = 0;
                }

                if (idle > 0) {
                    Thread.sleep(Math.max(idle * 200, 2000));
                }
            }
        } catch (IOException e) {
            // Be silent about IOExceptions ...
            Log.d(TAG,"Got catch ---  IOException");
            e.printStackTrace();
        } catch (InterruptedException e) {
            Log.d(TAG,"Got catch ---  InterruptedException");
            e.printStackTrace();
        }

        // We're exiting, usually because the in stream has been
        // closed. Whatever, close our streams. This will cause the
        // paired thread to exit too.
        try {
            Log.d(TAG,"close our stream");
            m_out.close();
            m_in.close();
        } catch (IOException e) {
            Log.d(TAG,"Got catch ---  1");
            e.printStackTrace();
        }
    }
}
