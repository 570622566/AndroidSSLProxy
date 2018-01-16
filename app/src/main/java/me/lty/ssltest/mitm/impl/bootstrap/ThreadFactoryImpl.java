package me.lty.ssltest.mitm.impl.bootstrap;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Describe
 * <p>
 * Created on: 2018/1/16 下午4:01
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
class ThreadFactoryImpl implements ThreadFactory {

    private final String namePrefix;
    private final ThreadGroup group;
    private final AtomicLong count;

    ThreadFactoryImpl(final String namePrefix, final ThreadGroup group) {
        this.namePrefix = namePrefix;
        this.group = group;
        this.count = new AtomicLong();
    }

    ThreadFactoryImpl(final String namePrefix) {
        this(namePrefix, null);
    }

    @Override
    public Thread newThread(final Runnable target) {
        return new Thread(this.group, target, this.namePrefix + "-"  + this.count.incrementAndGet());
    }
}
