package me.lty.ssltest.mitm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Describe
 * <p>
 * Created on: 2018/1/13 下午2:27
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class DefaultAsyncRunner implements AsyncRunner {

    private long requestCount;

    private final List<ConnectionHandler> running = Collections.synchronizedList(new ArrayList<ConnectionHandler>());

    /**
     * @return a list with currently running clients.
     */
    public List<ConnectionHandler> getRunning() {
        return running;
    }

    @Override
    public void closeAll() {
        // copy of the list for concurrency
        for (ConnectionHandler clientHandler : new ArrayList<>(this.running)) {
            clientHandler.close();
        }
    }

    @Override
    public void closed(ConnectionHandler clientHandler) {
        this.running.remove(clientHandler);
    }

    @Override
    public void exec(ConnectionHandler clientHandler) {
        ++this.requestCount;
        Thread t = new Thread(clientHandler);
        t.setDaemon(true);
        t.setName("Request Processor (#" + this.requestCount + ")");
        this.running.add(clientHandler);
        t.start();
    }
}
