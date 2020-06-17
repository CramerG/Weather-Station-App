package com.WWU.CyberEnvironment.BLE.repository;

import com.WWU.CyberEnvironment.BLE.authentication.CurrentUser;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RequestInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (!CurrentUser.key.equals("")) {
            request = request.newBuilder()
                    .addHeader("Authorization", "Token " + CurrentUser.key)
                    .build();
        }
        return chain.proceed(request);
    }
}
