package me.lty.ssltest.mitm;

/**
 * Describe
 * <p>
 * Created on: 2018/1/13 下午2:25
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public interface AsyncRunner {
    public void closeAll();
    public void closed(ConnectionHandler clientHandler);
    public void exec(ConnectionHandler clientHandler);
}
