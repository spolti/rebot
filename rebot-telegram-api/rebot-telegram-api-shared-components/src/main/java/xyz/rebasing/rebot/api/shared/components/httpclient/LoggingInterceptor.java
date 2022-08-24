package xyz.rebasing.rebot.api.shared.components.httpclient;

import java.io.IOException;
import java.lang.invoke.MethodHandles;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;

public class LoggingInterceptor implements Interceptor {

    private final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();

        long t1 = System.nanoTime();
        log.info(String.format("Sending request %s on %s%n%s with body %s",
                                  request.url(), chain.connection(), request.headers(), request.body().toString()));

        Response response = chain.proceed(request);

        long t2 = System.nanoTime();
        log.info(String.format("Received response for %s in %.1fms%n%s",
                                  response.request().url(), (t2 - t1) / 1e6d, response.headers()));

        return response;
    }
}
