package me.lty.ssltest.mitm.impl.bootstrap;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Describe
 * <p>
 * Created on: 2018/1/16 下午4:13
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
class ProxyWorkerPoolExecutor extends ThreadPoolExecutor {

    private final Map<ProxyWorker, Boolean> workerSet;

    public ProxyWorkerPoolExecutor(final int corePoolSize,
                                   final int maximumPoolSize,
                                   final long keepAliveTime,
                                   final TimeUnit unit,
                                   final BlockingQueue<Runnable> workQueue,
                                   final ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        this.workerSet = new ConcurrentHashMap<>();
    }

    @Override
    protected void beforeExecute(final Thread t, final Runnable r) {
        if (r instanceof ProxyWorker) {
            this.workerSet.put((ProxyWorker) r, Boolean.TRUE);
        }
    }

    @Override
    protected void afterExecute(final Runnable r, final Throwable t) {
        if (r instanceof ProxyWorker) {
            this.workerSet.remove(r);
        }
    }

    public Set<ProxyWorker> getWorkers() {
        return new HashSet<ProxyWorker>(this.workerSet.keySet());
    }
}
