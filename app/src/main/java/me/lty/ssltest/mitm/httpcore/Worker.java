/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package me.lty.ssltest.mitm.httpcore;

import org.apache.httpcore.ExceptionLogger;
import org.apache.httpcore.HttpHost;
import org.apache.httpcore.HttpServerConnection;
import org.apache.httpcore.protocol.BasicHttpContext;
import org.apache.httpcore.protocol.HttpCoreContext;
import me.lty.ssltest.mitm.httpcore.HttpService;

import java.io.IOException;

/**
 * @since 4.4
 */
public class Worker implements Runnable {

    private final HttpService httpservice;
    private final HttpServerConnection conn;
    private final HttpHost targetHost;
    private final ExceptionLogger exceptionLogger;

    private boolean isTLS;

    public Worker(
            final HttpService httpservice,
            final HttpServerConnection conn,
            final HttpHost targetHost,
            final ExceptionLogger exceptionLogger) {
        super();
        this.httpservice = httpservice;
        this.conn = conn;
        this.targetHost = targetHost;
        this.exceptionLogger = exceptionLogger;
    }

    public void setUseSSL(boolean secrecy) {
        isTLS = secrecy;
    }

    @Override
    public void run() {
        try {
            final BasicHttpContext localContext = new BasicHttpContext();
            final HttpCoreContext context = HttpCoreContext.adapt(localContext);
            context.setAttribute("targetHost",this.targetHost);
            context.setAttribute("isTLS",this.isTLS);
            while (!Thread.interrupted() && this.conn.isOpen()) {
                this.httpservice.handleRequest(this.conn, context);
                localContext.clear();
            }
            this.conn.close();
        } catch (final Exception ex) {
            this.exceptionLogger.log(ex);
        } finally {
            try {
                this.conn.shutdown();
            } catch (final IOException ex) {
                this.exceptionLogger.log(ex);
            }
        }
    }

}
