package xyz.rebasing.rebot.plugin.dimep;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import okhttp3.ConnectionSpec;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import xyz.rebasing.rebot.api.domain.Chat;
import xyz.rebasing.rebot.api.domain.Message;
import xyz.rebasing.rebot.api.shared.components.message.sender.OutcomeMessageProcessor;
import xyz.rebasing.rebot.plugin.dimep.utils.Utils;


/**
 * Ponto class implements the Job interface which is used to mark the point
 */
@ApplicationScoped
public class Ponto implements Job {

    private final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

    private final String DIMEP_ENDPOINT = "https://www.dimepkairos.com.br/Dimep/Account/Marcacao";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .cookieJar(new CookieJar() {
                private List<Cookie> cookies;

                @Override
                public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                    this.cookies = cookies;
                }

                @Override
                public List<Cookie> loadForRequest(HttpUrl url) {
                    return cookies != null ? cookies : List.of();
                }
            })
            .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .connectTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
            .readTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
            .writeTimeout(2, java.util.concurrent.TimeUnit.MINUTES)
            .build();
    @Inject
    @ConfigProperty(name = "xyz.rebasing.rebot.plugin.dimep.username")
    String username;
    // For now, this password is base64 encoded password, if needed we will improve it later.
    @Inject
    @ConfigProperty(name = "xyz.rebasing.rebot.plugin.dimep.password")
    String password;
    @Inject
    @ConfigProperty(name = "xyz.rebasing.rebot.plugin.dimep.chatId")
    Long chatId;
    @Inject
    OutcomeMessageProcessor messageSender;
    @Inject
    Scheduler scheduler;

    @Override
    public void execute(JobExecutionContext context) {
        // Define the job's behavior here
        log.infof("Executing scheduled job %s", context.getJobDetail().getKey());
        this.goPonto(Utils.formatDate(ZonedDateTime.now().toInstant()), context.getJobDetail().getKey().getName());
        try {
            // unregister for the next day marks
            scheduler.unscheduleJob(context.getTrigger().getKey());
            scheduler.deleteJob(context.getJobDetail().getKey());
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Mark the point
     * @param dateMarking current date/time to mark
     * @param name name of the job, just for notification and logs purpose
     */
    public void goPonto(String dateMarking, String name) {
        RequestBody formBody = new FormBody.Builder()
                .add("UserName", Utils.base64decode(username))
                .add("Password", Utils.base64decode(password))
                .add("DateMarking", dateMarking)
                .build();

        Request markRequest = new Request.Builder()
                .url(DIMEP_ENDPOINT)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Safari/605.1.15")
                .build();

        log.debug("Executing goPonto with payload: " + requestBodyToString(formBody));

        try (Response markResponse = client.newCall(markRequest).execute()) {
            if (!markResponse.isSuccessful()) {
                System.out.println(markResponse.body().string());
                throw new IOException("Unexpected code " + markResponse);
            }
            BufferedReader reader = new BufferedReader(new StringReader(markResponse.body().string()));
            Optional<String> downloadUrl = reader.lines().skip(50)
                    .filter(s -> s.contains("dimepbr-comprovanteponto"))
                    .map(s -> s.split("=")[1].replace("\"", "").replace("type", "").trim())
                    .findFirst();

            log.infof("Comprovante --  " + downloadUrl.get());

            downloadUrl.ifPresent(s -> messageSender.processOutgoingMessage(
                    new Message(0, new Chat(chatId), "Success " + name + " at " + dateMarking +
                            "\nDownload receipt " + s),
                    false,
                    0));
        } catch (Exception e) {
            log.error("Error marking the point", e);
            messageSender.processOutgoingMessage(
                    new Message(0, new Chat(chatId), "Failed to persist the mark " + name + " at " + dateMarking),
                    false,
                    0);
        }
    }

    /**
     * unschedule a job by name
     * @param jobName name of the job to unschedule
     * @return true if the job was unscheduled, false otherwise
     */
    public boolean unscheduleJob(String jobName) {
        try {
            return scheduler.deleteJob(new org.quartz.JobKey(jobName, PontoTask.JOB_GROUP));
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    private String requestBodyToString(RequestBody requestBody) {
        try {
            Buffer buffer = new Buffer();
            requestBody.writeTo(buffer);
            return buffer.readUtf8();
        } catch (IOException e) {
            return "Error converting RequestBody to String: " + e.getMessage();
        }
    }
}
