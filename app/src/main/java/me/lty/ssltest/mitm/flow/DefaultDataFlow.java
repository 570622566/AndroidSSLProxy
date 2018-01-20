package me.lty.ssltest.mitm.flow;

import android.util.Log;

import org.apache.httpcore.HttpException;
import org.apache.httpcore.HttpRequest;
import org.apache.httpcore.HttpResponse;
import org.apache.httpcore.HttpStatus;
import org.apache.httpcore.protocol.HttpContext;

import java.io.IOException;

import me.lty.ssltest.mitm.Firebase;
import me.lty.ssltest.mitm.FirebaseNIO;
import me.lty.ssltest.mitm.FirebaseOkHttp;

/**
 * Describe
 * <p>
 * Created on: 2018/1/18 下午1:45
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class DefaultDataFlow implements DataFlow {

    @Override
    public void handle(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext
            httpContext) throws HttpException, IOException {
        HttpRequest request = doBeforeRequestProcess(httpRequest);

        HttpResponse realResponse = doRequest(request, httpContext);
        if (realResponse != null)
            doAfterResponseProcess(realResponse, httpResponse);
    }

    private HttpResponse doRequest(HttpRequest request, HttpContext context) {
        //return Firebase.getInstance().openFire(request, context);
        //return FirebaseNIO.getInstance().openFire(request, context);
        return FirebaseOkHttp.getInstance().openFire(request, context);
    }

    @Override
    public HttpRequest doBeforeRequestProcess(HttpRequest request) {
        Log.d("LLYH",request.getRequestLine().getUri());
        return request;
    }

    @Override
    public void doAfterResponseProcess(HttpResponse realResponse, HttpResponse defResponse) {
        defResponse.setStatusLine(realResponse.getStatusLine());
        defResponse.setHeaders(realResponse.getAllHeaders());

        final int status = defResponse.getStatusLine().getStatusCode();
        boolean hasBody =  status >= HttpStatus.SC_OK
                && status != HttpStatus.SC_NO_CONTENT
                && status != HttpStatus.SC_NOT_MODIFIED
                && status != HttpStatus.SC_RESET_CONTENT;
        if (hasBody) {
            defResponse.setEntity(realResponse.getEntity());
        }
    }

}
