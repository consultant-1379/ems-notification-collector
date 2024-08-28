/*******************************************************************************
 * COPYRIGHT Ericsson 2021
 *
 *
 *
 * The copyright to the computer program(s) herein is the property of
 *
 * Ericsson Inc. The programs may be used and/or copied only with written
 *
 * permission from Ericsson Inc. or in accordance with the terms and
 *
 * conditions stipulated in the agreement/contract under which the
 *
 * program(s) have been supplied.
 ******************************************************************************/
package com.ericsson.oss.adc.emsnc.scheduling;

import com.ericsson.oss.adc.emsnc.processing.EmsInfoService;
import com.ericsson.oss.adc.emsnc.processing.NotificationSubscriptionService;
import com.ericsson.oss.adc.emsnc.processing.data.EnmInfo;
import com.ericsson.oss.adc.emsnc.processing.data.PollingSubscription;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.PersistJobDataAfterExecution;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

@Slf4j
@RequiredArgsConstructor
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class ConnectedSystemsPollingJob extends QuartzJobBean {

  @Value("${emsnc.timing.ems-polling-cron-schedule}")
  public String emsPollingCronSchedule;

  public static final String JDM_KEY_ENM_INFO = "enm-info";
  public static final String JDM_KEY_SUBSCRIPTION = "polling-subscription";
  public static final String GROUP_NAME = "enm-polling";

  private final EmsInfoService emsInfoService;
  private final NotificationSubscriptionService notificationSubscriptionService;
  private final SchedulerFactoryBean schedulerFactoryBean;

  @SneakyThrows // TODO check exceptions
  @Override
  protected void executeInternal(JobExecutionContext jobExecutionContext)
      throws JobExecutionException {

    Scheduler scheduler = schedulerFactoryBean.getScheduler();
    try {
      List<EnmInfo> enms = emsInfoService.getCurrentListOfEnmInstances();
      Set<JobKey> validJobs = createValidJobs(scheduler, enms);

      // clean up old, invalid jobs
      Set<JobKey> existingJobs =
          new HashSet<>(scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP_NAME)));
      existingJobs.removeAll(validJobs);
      for (JobKey invalidJob : existingJobs) {
        scheduler.deleteJob(invalidJob);
      }
    } catch (Exception e) {
      // could not update list of ENMS, but still proceed with updating tokens
      log.error("Failed to update list of ENMs", e);
    }
  }

  private Set<JobKey> createValidJobs(Scheduler scheduler, List<EnmInfo> enms)
      throws SchedulerException {
    Set<JobKey> validJobs = new HashSet<>();
    for (EnmInfo enmInfo : enms) {
      for (PollingSubscription subscription :
          notificationSubscriptionService.getPollingSubscriptions()) {
        UUID jobKeyId =
            UUID.nameUUIDFromBytes(
                (Normalizer.normalize(enmInfo.getEnmUri().toString(), Normalizer.Form.NFKC)
                        + "-"
                        + Normalizer.normalize(subscription.getNeType(), Normalizer.Form.NFKC))
                    .getBytes(StandardCharsets.UTF_8));
        JobKey key = JobKey.jobKey(String.valueOf(jobKeyId), GROUP_NAME);
        if (!scheduler.checkExists(key)) {
          createJob(scheduler, enmInfo, subscription, key);
        }
        validJobs.add(key);
      }
    }
    return validJobs;
  }

  private void createJob(
      Scheduler scheduler, EnmInfo enmInfo, PollingSubscription subscription, JobKey key)
      throws SchedulerException {
    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put(JDM_KEY_ENM_INFO, enmInfo);
    jobDataMap.put(JDM_KEY_SUBSCRIPTION, subscription);

    JobDetail jobDetail =
        JobBuilder.newJob(EmsPollingJob.class)
            .withIdentity(key)
            .usingJobData(jobDataMap)
            .requestRecovery()
            .storeDurably()
            .build();

    TriggerKey triggerKey = TriggerKey.triggerKey(key.getName(), key.getGroup());
    CronTrigger trigger =
        TriggerBuilder.newTrigger()
            .withIdentity(triggerKey)
            .forJob(jobDetail)
            .withSchedule(
                CronScheduleBuilder.cronSchedule(emsPollingCronSchedule)
                    .withMisfireHandlingInstructionIgnoreMisfires())
            .build();

    scheduler.scheduleJob(jobDetail, trigger);
  }
}
