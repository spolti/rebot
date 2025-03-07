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

package xyz.rebasing.rebot.plugin.packt.notifier;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.quarkus.scheduler.Scheduled;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jboss.logging.Logger;
import xyz.rebasing.rebot.api.domain.Chat;
import xyz.rebasing.rebot.api.domain.Message;
import xyz.rebasing.rebot.api.domain.MessageUpdate;
import xyz.rebasing.rebot.api.i18n.I18nHelper;
import xyz.rebasing.rebot.api.shared.components.message.sender.OutcomeMessageProcessor;
import xyz.rebasing.rebot.plugin.packt.domain.DailyOffer;
import xyz.rebasing.rebot.service.persistence.domain.PacktNotification;
import xyz.rebasing.rebot.service.persistence.repository.PacktRepository;

@ApplicationScoped
public class PacktNotifier {

    private static final String FREE_LEARNING_URL = "https://www.packtpub.com/free-learning";
    private static final String PACKT_DAILY_OFFER_ENDPOINT = "https://static.packt-cdn.com/products/%s/summary";
    private final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
    Cache<String, Object> cache = Caffeine.newBuilder().build();
    @Inject
    ObjectMapper objectMapper;
    @Inject
    private OutcomeMessageProcessor messageSender;
    @Inject
    private PacktRepository repository;

    public String get(String locale) {
        DailyOffer dailyOffer = (DailyOffer) cache.getIfPresent("book");
        String pages = null != dailyOffer.getPages() ? dailyOffer.getPages().toString() : "N/A";
        return String.format(
                I18nHelper.resource("Packt", locale, "book"),
                dailyOffer.getTitle(),
                dailyOffer.getOneLiner(),
                pages,
                FREE_LEARNING_URL);
    }

    @Scheduled(cron = "0 30 05 * * ?")
    public void populate() {

        if (log.isDebugEnabled()) {
            log.debug("Retrieving information about the daily free ebook.");
        }

        OkHttpClient client = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
                .build();

        // need to query for the product id now, updated, again.
        Request loadDailyOfferRequest = new Request.Builder()
                .url(FREE_LEARNING_URL)
                .get()
                .build();

        try (Response response = client.newCall(loadDailyOfferRequest).execute()) {

            if (response.code() != 200) {
                log.warnv("Failed to connect in the endpoint {0}, status code is: {1}",
                        loadDailyOfferRequest.url(),
                        response.code());
            }

            BufferedReader reader = new BufferedReader(new StringReader(response.body().string()));
            // navigate directly more or lest to where the product ID is and get the 'const metaProductId' value
            // it is at the end of the html page.
            Optional<String> metaProductId = reader.lines().skip(1000)
                    .filter(s -> s.contains("const metaProductId"))
                    .map(s -> s.split("=")[1].replace("\'", "").replace(";", "").trim())
                    .findFirst();

            if (metaProductId.isPresent()) {
                Request getDailyOfferRequest = new Request.Builder()
                        .url(String.format(PACKT_DAILY_OFFER_ENDPOINT, metaProductId.get()))
                        .get()
                        .build();

                Response getDailyOfferResponse = client.newCall(getDailyOfferRequest).execute();
                if (log.isTraceEnabled()) {
                    log.tracev("Getting daily pack offer with url: {0}", getDailyOfferRequest.url());
                }
                if (getDailyOfferResponse.code() != 200) {
                    log.warnv("Failed to connect in the endpoint {0}, status code is: {1}",
                            getDailyOfferRequest.url(), getDailyOfferResponse.code());
                }

                DailyOffer dailyOffer = objectMapper.readValue(getDailyOfferResponse.body().string(), DailyOffer.class);
                if (log.isDebugEnabled()) {
                    log.debug(dailyOffer.toString());
                }

                cache.invalidateAll();
                cache.put("book", dailyOffer);

                repository.get().stream().forEach(packtNotification ->
                        this.notify(packtNotification.getChatId(),
                                packtNotification.getLocale())
                );
            }
        } catch (final Exception e) {
            log.warnv("Failed to obtain the ebook information: {0}", e.getMessage());
        }
    }

    public String registerNotification(MessageUpdate message, String locale) {
        String channel;
        if ("group".equals(message.getMessage().getChat().getType()) || "supergroup".equals(message.getMessage().getChat().getType())) {
            channel = message.getMessage().getChat().getTitle();
        } else {
            channel = message.getMessage().getFrom().getFirstName();
        }
        return repository.register(new PacktNotification(message.getMessage().getChat().getId(),
                channel,
                locale));
    }

    public String unregisterNotification(MessageUpdate message, String locale) {
        String channel;
        if ("group".equals(message.getMessage().getChat().getType()) || "supergroup".equals(message.getMessage().getChat().getType())) {
            channel = message.getMessage().getChat().getTitle();
        } else {
            channel = message.getMessage().getFrom().getFirstName();
        }
        return repository.unregister(new PacktNotification(message.getMessage().getChat().getId(),
                channel,
                locale));
    }

    private void notify(Long chatId, String locale) {
        messageSender.processOutgoingMessage(new Message(0, new Chat(chatId), this.get(locale)),
                false,
                0);
    }
}