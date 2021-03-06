package me.lty.ssltest.mitm.impl.bootstrap;/*
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

import android.support.annotation.NonNull;
import android.util.Log;
import android.util.LruCache;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import me.lty.ssltest.App;
import me.lty.ssltest.Config;
import me.lty.ssltest.mitm.filter.ProxyDataFilter;
import me.lty.ssltest.mitm.factory.MITMPlainSocketFactory;
import me.lty.ssltest.mitm.factory.MITMSSLSocketFactory;
import me.lty.ssltest.mitm.filter.RequestDataFilter;
import me.lty.ssltest.mitm.filter.ResponseDataFilter;

//import me.lty.ssltest.mitm.engine.HTTPSProxyEngine;

/**
 * Main class for the Man In The Middle SSL proxy.  Delegates the real work
 * to HTTPSProxyEngine.
 * <p>
 * NOTE: This code was originally developed as a project for use in the CS255 course at Stanford,
 * taught by Professor Dan Boneh.
 *
 * @author Srinivas Inguva
 */

public class MITMProxyServer {

    private static final String TAG = MITMProxyServer.class.getSimpleName();
    private final LruCache<String, MITMSSLSocketFactory> mSsfCache;

    enum Status {READY, ACTIVE, STOPPING;}

    private final int port;

    private ProxyDataFilter requestFilter;
    private ProxyDataFilter responseFilter;

    private ServerSocket mServerSocket;
    private String localHost = Config.PROXY_SERVER_LISTEN_HOST;

    public MITMProxyServer(final int port) {
        this.port = port;


        // Default values.
        requestFilter = new RequestDataFilter();
        responseFilter = new ResponseDataFilter();

        String filename = App.context().getFilesDir() + "/out.txt";

        try {
            PrintWriter pw = new PrintWriter(new FileWriter(filename), true);
            requestFilter.setOutputPrintWriter(pw);
            responseFilter.setOutputPrintWriter(pw);
        } catch (Exception e) {
            e.printStackTrace();
        }

        final StringBuffer startMessage = new StringBuffer();

        startMessage.append("Initializing SSL proxy with the parameters:" +
                                    "\n   Local host:       " + localHost +
                                    "\n   Local port:       " + port);
        startMessage.append("\n   (SSL setup could take a few seconds)");

        Log.i(TAG, startMessage.toString());

        try {
            mServerSocket = new MITMPlainSocketFactory().createServerSocket(
                    localHost,
                    port
            );
            Log.i(TAG, "Proxy initialized, listening on port " + port);
        } catch (IOException e) {
            Log.e(TAG, "Could not initialize proxy:");
            e.printStackTrace();
        }

        mSsfCache = new LruCache<>(30);
    }

    public void start() throws IOException {
        ThreadGroup threadGroup = new ThreadGroup("Proxy-workers");
        WorkerPoolExecutor workerPoolExecutor = new WorkerPoolExecutor(
                0,
                Integer.MAX_VALUE,
                1L,
                TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(),
                new ThreadFactoryImpl(
                        "Proxy-worker",
                        threadGroup
                )
        );

        do {
            try {
                Socket finalAccept = mServerSocket.accept();
                Worker worker = createWorker(finalAccept);
                workerPoolExecutor.execute(worker);
            } catch (Exception e) {
                Log.e(TAG, "Could not initialize proxy:");
                e.printStackTrace();
            }
        } while (!mServerSocket.isClosed());

        Log.i(TAG, "Engine exited");
    }

    @NonNull
    private Worker createWorker(Socket finalAccept) throws Exception {
        return new Worker(
                finalAccept,
                new MITMSSLSocketFactory(),
                requestFilter,
                responseFilter,
                localHost,
                this.port,
                mSsfCache
        );
    }
}
