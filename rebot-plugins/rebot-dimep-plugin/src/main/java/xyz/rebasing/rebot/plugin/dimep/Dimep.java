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

package xyz.rebasing.rebot.plugin.dimep;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.jboss.logging.Logger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import xyz.rebasing.rebot.api.conf.BotConfig;
import xyz.rebasing.rebot.api.domain.MessageUpdate;
import xyz.rebasing.rebot.api.spi.CommandProvider;
import xyz.rebasing.rebot.plugin.dimep.utils.Utils;


/**
 * Base command provider for Dimpe plugin
 */
@ApplicationScoped
public class Dimep implements CommandProvider {

    private final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

    @Inject
    BotConfig config;

    @Inject
    Scheduler scheduler;

    @Inject
    PontoTask pontoTask;

    @Inject
    Ponto ponto;

    @Override
    public void load() {
        log.debugv("Loading command {0}", this.name());
    }

    @Override
    public Object execute(Optional<String> key, MessageUpdate messageUpdate, String locale) {

        if (14289485 != messageUpdate.getMessage().getFrom().getId()) {
            return "You're not allowed to run this command: " + messageUpdate.getMessage().getFrom().getUsername();
        }

        ZonedDateTime today = ZonedDateTime.now();
        StringBuilder message = new StringBuilder("<code>Found triggers for today: </code>\n");

        if (key.isPresent()) {
            String command = key.get().split(" ")[0];
            String[] parts = key.get().split(" ");
            String result = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
            log.infof("Command: %s, args: %s", command, result);
            switch (command) {
                case "startday":
                    try {
                        return pontoTask.schedule();
                    } catch (SchedulerException e) {
                        throw new RuntimeException(e);
                    }
                case "mark":
                    ponto.goPonto(Utils.formatDate(today.toInstant()), "singleMark");
                    return "Mark requested at " + today;
                case "interrupt":
                    // TODO interrupt job by name
                    String[] args = result.split(" ");
                    if (args.length == 0) {
                        return "Interrupt requested, but no job name provided";
                    }
                    boolean ok = ponto.unscheduleJob(args[0]);
                    if (ok) {
                        return "Interrupt requested, name: " + args[0] + " - OK";
                    }
                    return "Interrupt requested failed, name: " + args[0];
                default:
                    break;
            }
        }

        try {
            Set<JobKey> jobKeys = scheduler.getJobKeys(GroupMatcher.groupEquals(PontoTask.JOB_GROUP));
            Set<JobKey> filteredJobKeys = jobKeys.stream()
                    .filter(jobKey -> jobKey.getName().contains(today.toLocalDate().toString()))
                    .collect(Collectors.toSet());
            for (JobKey jobKey : filteredJobKeys) {
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                TriggerKey triggerKey = new TriggerKey(jobDetail.getKey().getName(), jobDetail.getKey().getGroup());

                log.infof("Job name: %s, Job group: %s, scheduled at %s",
                        jobDetail.getKey().getName(),
                        jobDetail.getKey().getGroup(),
                        scheduler.getTrigger(triggerKey).getNextFireTime());

                message.append("<b>Job Name:</b> ").append(jobDetail.getKey().getName()).append("\n");
                message.append("<b>Scheduled At:</b> ").append(scheduler.getTrigger(triggerKey).getNextFireTime()).append("\n\n");
            }
        } catch (SchedulerException e) {
            log.error("Error listing scheduled jobs", e);
        }

        return message;
    }

    @Override
    public String name() {
        return "/dimep";
    }

    @Override
    public String help(String locale) {
        StringBuilder help = new StringBuilder("Available commands:\n");
        help.append("/dimep startday - Schedule the first mark of the day\n");
        help.append("/dimep mark - Mark point manually\n");
        help.append("/dimep interrupt <job-name> - Interrupt a scheduled job\n");
        help.append("/dimep - List all scheduled jobs for today\n");
        return help.toString();
    }

    @Override
    public String description(String locale) {
        return "dimep - marcação de ponto";
    }

    @Override
    public boolean deleteMessage() {
        return config.deleteMessages();
    }

    @Override
    public long deleteMessageTimeout() {
        return config.deleteMessagesAfter();
    }
}