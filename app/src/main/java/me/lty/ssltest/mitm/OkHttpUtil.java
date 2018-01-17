package me.lty.ssltest.mitm;

import android.support.annotation.Nullable;

import java.io.IOException;

import me.lty.ssltest.BuildConfig;
import me.lty.ssltest.mitm.nanohttp.MyRequest;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/**
 * Describe
 * <p>
 * Created on: 2018/1/17 下午4:05
 * Email: lty81372860@sina.com
 * <p>
 * Copyright (c) 2018 lty. All rights reserved.
 * Revision：
 *
 * @author lty
 * @version v1.0
 */
public class OkHttpUtil {

    private static OkHttpUtil mInstance;
    private static OkHttpClient mOkHttpClient;

    public static OkHttpUtil getInstance() {
        if (mInstance == null) {
            synchronized (OkHttpUtil.class) {
                OkHttpUtil util = new OkHttpUtil();
                util.initOkHttpClient();
                mInstance = util;
            }
        }
        return mInstance;
    }

    private void initOkHttpClient() {
        OkHttpClient.Builder httpBuilder = new OkHttpClient().newBuilder();
        mOkHttpClient = httpBuilder.retryOnConnectionFailure(true).build();
    }

    public MyResponse doRequest(MyRequest request) {
        RequestBody body = null;

        final long bodySize = request.getBodySize();
        final byte[] requestBody = request.getRequestBody();
        if (requestBody != null) {
            MediaType contentType = null;
            request.headers.containsKey("content-type")
            body = new RequestBody() {
                @Nullable
                @Override
                public MediaType contentType() {
                    return contentType;
                }

                @Override
                public long contentLength() throws IOException {
                    return bodySize;
                }

                @Override
                public void writeTo(BufferedSink sink) throws IOException {
                    sink.write(requestBody, 0, (int)bodySize);
                }
            };
        }

        Request request1 = new Request.Builder()
                .method(request.method.name(), body)
                .build();

        return null;
    }
}
