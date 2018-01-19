package me.lty.ssltest.mitm.flow;

import org.apache.httpcore.HttpRequest;
import org.apache.httpcore.HttpResponse;
import org.apache.httpcore.protocol.HttpRequestHandler;

/**
 * Describe
 * <p>
 * Created on: 2018/1/18 下午1:39
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public interface DataFlow extends HttpRequestHandler{

    HttpRequest doBeforeRequestProcess(HttpRequest request);

    void doAfterResponseProcess(HttpResponse realResponse, HttpResponse defResponse);

}
