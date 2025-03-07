package xyz.rebasing.rebot.plugin.dimep;

import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Random;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import xyz.rebasing.rebot.api.shared.components.message.sender.OutcomeMessageProcessor;
import xyz.rebasing.rebot.plugin.dimep.utils.Utils;

/**
 * PontoTask class
 * Contains the scheduler for the Ponto task
 */
@ApplicationScoped
public class PontoTask {

    /**
     * JOB_GROUP holds the group name for the jobs that will be scheduled
     */
    public static final String JOB_GROUP = "ponto";
    private final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
    @Inject
    Scheduler scheduler;
    @Inject
    Ponto ponto;
    @Inject
    OutcomeMessageProcessor messageSender;

    @Inject
    @ConfigProperty(name = "xyz.rebasing.rebot.plugin.dimep.chatId")
    Long chatId;

    /**
     * Schedule the initial task of the day, all the others will be done a soon as this is finished.
     * the next marks will be calculated depending on the first one. it will always mark as completed 8h and a few
     * extra minutes.
     */
    //@Scheduled(cron = "0 0 8 ? * MON-FRI", identity = "ponto")
    String schedule() throws SchedulerException {
        ZonedDateTime startTime = null;
        // do the first mark:
        Random random = new Random();
        // get a random number between 0 and 45 to mark the frst time and not be repeated during other days
        // consider this as minutes.
        // int differStartTime = random.nextInt(46);
        //int differStartTime = random.nextInt(0);
        //  try {
        // log.infof("Waiting %s minute(s) for the first mark", differStartTime);
        //Thread.sleep(differStartTime * 60_000);
        startTime = ZonedDateTime.now();
        ponto.goPonto(Utils.formatDate(startTime.toInstant()), "StartDay");
        //  } catch (InterruptedException e) {
        //    log.error("Sleep interrupted", e);
        //  Thread.currentThread().interrupt();
        // }

        // mark time for the lunch start
        // add 210 minutes fixed (3h30 hours), plus the random time
        ZonedDateTime lunchStart = startTime.plus(Duration.ofMinutes(210 + random.nextInt(21)));

        // mark time for the lunch end , let's make the lunch last for between 35 and 55 minutes
//        ZonedDateTime lunchEnd = lunchStart.plusMinutes(Utils.getRandomNumberBetween45And55());
        // use 60 minutes for now
        ZonedDateTime lunchEnd = lunchStart.plusMinutes(60);

        // Now, we need to calculate the working hours to see what is missing.
        Duration morning = Duration.between(startTime, lunchStart);
        Duration totalWorkTime = Duration.ofHours(8);
        Duration remainingWorkTime = totalWorkTime.minus(morning);
        int randomExtraMinutes = new Random().nextInt(6); // 0 to 6 minutes of spare time
        Duration afternoonWorkTime = remainingWorkTime.plusMinutes(randomExtraMinutes);
        ZonedDateTime dayEnd = lunchEnd.plus(afternoonWorkTime);

        Duration afterLunch = Duration.between(lunchEnd, dayEnd);
        Duration totalHours = morning.plus(afterLunch);

        log.debugf("start: %s \n## lunchStart: %s \n## lunchEnd: %s \n## worked hours morning: %s \n## end of the day mark: %s \n## working hours: %s ##",
                startTime,
                lunchStart,
                lunchEnd,
                morning,
                dayEnd,
                totalHours);

        // schedule the next tasks
        // start lunch break
//        JobDetail jobLunchStart = JobBuilder.newJob(Ponto.class)
//                .withIdentity("lunchStart-" + startTime.toLocalDate(), JOB_GROUP).build();
//        Trigger triggerLunchStart = TriggerBuilder.newTrigger()
//                .withIdentity("lunchStart-" + startTime.toLocalDate(), JOB_GROUP)
//                .startAt(Date.from(lunchStart.toInstant()))
//                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
//                .build();
//
//        // end lunch break
//        JobDetail jobLunchEnd = JobBuilder.newJob(Ponto.class)
//                .withIdentity("lunchEnd-" + startTime.toLocalDate(), JOB_GROUP).build();
//        Trigger triggerLunchEnd = TriggerBuilder.newTrigger()
//                .withIdentity("lunchEnd-" + startTime.toLocalDate(), JOB_GROUP)
//                .startAt(Date.from(lunchEnd.toInstant()))
//                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
//                .build();

        // end the day
        JobDetail jobDayEnd = JobBuilder.newJob(Ponto.class)
                .withIdentity("jobDayEnd-" + startTime.toLocalDate(), JOB_GROUP).build();
        Trigger triggerDayEnd = TriggerBuilder.newTrigger()
                .withIdentity("jobDayEnd-" + startTime.toLocalDate(), JOB_GROUP)
                .startAt(Date.from(dayEnd.toInstant()))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build();

        try {
//            scheduler.scheduleJob(jobLunchStart, triggerLunchStart);
//            log.infof("Lunch start scheduled to run at: %s ", Date.from(lunchStart.toInstant()));
//            scheduler.scheduleJob(jobLunchEnd, triggerLunchEnd);
//            log.infof("Lunch end scheduled to run at: %s", Date.from(lunchEnd.toInstant()));
            scheduler.scheduleJob(jobDayEnd, triggerDayEnd);
            log.infof("Day end scheduled to run at: %s", Date.from(dayEnd.toInstant()));

            return "Scheduled tasks for today done - Worked time for today will be " + totalHours;
            // scheduler.scheduleJob(testJob, testTrigger);
        } catch (Exception e) {
            log.errorf("Failed to trigger scheduler: %s", e.getMessage(), e);
            // notify the bot
            return e.getMessage();
        }
    }
}
