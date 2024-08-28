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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@SuppressWarnings("java:S2142") // intentionally not interrupting the thread
@Slf4j
@Component
@Data
@DisallowConcurrentExecution
public class EmsPollingJob extends QuartzJobBean {

  private int retryWaitTimeInSec = 30;

  @Autowired private EmsPollingService emsPollingService;

  @Override
  protected void executeInternal(JobExecutionContext jobExecutionContext)
      throws JobExecutionException {

    try {
      String jobKeyName = jobExecutionContext.getJobDetail().getKey().getName();
      String jobKeyGroup = jobExecutionContext.getJobDetail().getKey().getGroup();
      Date scheduledFireTime = jobExecutionContext.getScheduledFireTime();
      Date actualFireTime = jobExecutionContext.getFireTime();
      JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
      emsPollingService.doEmsPolling(
          jobKeyName, jobKeyGroup, scheduledFireTime, actualFireTime, jobDataMap);
    } catch (InterruptedException | ExecutionException | TimeoutException | RuntimeException e) {
      var jobExecutionException = new JobExecutionException(e);
      var fireInstant = jobExecutionContext.getScheduledFireTime().toInstant();
      Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
      jobExecutionException.setRefireImmediately(true);

      if (fireInstant.isBefore(twoHoursAgo)) {
        jobExecutionException.setRefireImmediately(false);
      } else {
        waitForRetry();
      }

      throw jobExecutionException;
    }
  }

  private void waitForRetry() {
    try {
      TimeUnit.SECONDS.sleep(retryWaitTimeInSec);
    } catch (InterruptedException interruptedException) {
      log.info("Thread sleep interrupted: {}", interruptedException.getMessage());
    }
  }
}
