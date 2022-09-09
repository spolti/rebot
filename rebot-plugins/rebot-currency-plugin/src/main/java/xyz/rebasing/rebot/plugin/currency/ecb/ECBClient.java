/*
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2017 Rebasing.xyz ReBot
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package xyz.rebasing.rebot.plugin.currency.ecb;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.quarkus.scheduler.Scheduled;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jboss.logging.Logger;
import xyz.rebasing.rebot.service.persistence.repository.EcbRepository;

@ApplicationScoped
public class ECBClient {

    private final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

    public static final String ECB_XML_ADDRESS = "http://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";

    @Inject
    EcbSaxHandler handler;

    @Inject
    EcbRepository repository;

    Cache<String, Object> c = Caffeine.newBuilder().build();

    public Cache<String, Object> cache() {
        return c;
    }

    @Scheduled(every = "12h", delay = 60)
    public void getAndPersistDailyCurrencies() {
        OkHttpClient client = new OkHttpClient.Builder()
                .build();
        Request request = new Request.Builder()
                .url(ECB_XML_ADDRESS)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            log.debugv("Parsing currencies from {0}", ECB_XML_ADDRESS);

            SAXParserFactory saxParser = SAXParserFactory.newInstance();
            saxParser.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            saxParser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            try (InputStream input = response.body().byteStream()) {
                if (input != null) {
                    saxParser.newSAXParser().parse(response.body().byteStream(), handler);
                } else {
                    throw new NullPointerException("Response is null");
                }
            }

            repository.persist(handler.cubes());
            c.cleanUp();
            handler.cubes().getCubes().forEach(cube -> {
                c.put(cube.getCurrency(), cube);
                c.put("time", handler.cubes().getTime());
            });

        } catch (final Exception e) {
            log.errorv("Error to retrieve currency rates from {0} - message: {1}", ECB_XML_ADDRESS, e.getMessage());
        } finally {
            handler.clean();
        }
    }
}