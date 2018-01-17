package me.lty.ssltest.mitm.filter;/*
Copyright 2007 Srinivas Inguva

Redistribution and use in source and binary forms, with or without modification, are permitted
provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of
    * conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, this list of
    * conditions and the following disclaimer in the documentation and/or other materials
    * provided with the distribution.
    * Neither the name of Stanford University nor the names of its contributors may be used to
    * endorse or promote products derived from this software without specific prior written
    * permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

import android.util.Log;

import java.io.PrintWriter;

import me.lty.ssltest.mitm.ConnectionDetails;

/*
 * This class is used to record data that passes back and forth over a TCP
 * connection.  Output goes to a PrintWriter, whose default value is System.out.
 *
 * NOTE: While this class just writes the data that is associated with a connection,
 *       it can easily be changed to perform more complex behavior such as modifying
 *       HTTP requests/responses.
 *
 * @author Srinivas Inguva
 
 */

public class ProxyDataFilter {

    private static final String TAG = ProxyDataFilter.class.getSimpleName();

    private PrintWriter m_out = new PrintWriter(System.out, true);

    public void setOutputPrintWriter(PrintWriter outputPrintWriter) {
        m_out.flush();
        m_out = outputPrintWriter;
    }

    public PrintWriter getOutputPrintWriter() {
        return m_out;
    }

    public void handle(String tag, ConnectionDetails connectionDetails, byte[] buffer, int
            bytesRead) throws java.io.IOException {

        final StringBuffer stringBuffer = new StringBuffer();

        Hex2Char(buffer, bytesRead, stringBuffer);

        m_out.println("------ " + connectionDetails.getDescription() +
                              " ------");
        m_out.println(stringBuffer);

        Log.wtf(tag, stringBuffer.toString());
    }

    private void Hex2Char(byte[] buffer, int bytesRead, StringBuffer stringBuffer) {
        boolean inHex = false;

        for (int i = 0; i < bytesRead; i++) {
            final int value = (buffer[i] & 0xFF);

            // If it's ASCII, print it as a char.
            if (value == '\r' || value == '\n' ||
                    (value >= ' ' && value <= '~')) {

                if (inHex) {
                    stringBuffer.append(']');
                    inHex = false;
                }

                stringBuffer.append((char) value);
            } else { // else print the value
                if (!inHex) {
                    stringBuffer.append('[');
                    inHex = true;
                }

                if (value <= 0xf) { // Where's "HexNumberFormatter?"
                    stringBuffer.append("0");
                }

                stringBuffer.append(Integer.toHexString(value).toUpperCase());
            }
        }
    }

    public void connectionOpened(ConnectionDetails connectionDetails) {
        m_out.println("--- " + connectionDetails.getDescription() +
                              " opened --");
    }

    public void connectionClosed(ConnectionDetails connectionDetails) {
        m_out.println("--- " + connectionDetails.getDescription() +
                              " closed --");
    }
}



