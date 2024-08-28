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

import com.ericsson.oss.adc.emsnc.IsolationLevel;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class JobConfig {

  @Value("${emsnc.kafka.emsnc-internal.producer-isolation}")
  private IsolationLevel emsncKafkaIsolation;

  @Value("${emsnc.timing.connected-systems-polling-cron-schedule}")
  private String connectedSystemsPollingCronSchedule;

  @Bean(name = "connected-systems-polling-job")
  public JobDetail connectedSystemsPollingJob() {
    return JobBuilder.newJob(ConnectedSystemsPollingJob.class)
        .requestRecovery()
        .withIdentity("connected-systems-polling-job")
        .storeDurably(true)
        .build();
  }

  @Bean(name = "connected-systems-polling-trigger")
  public Trigger connectedSystemsPollingTrigger(
      @Qualifier("connected-systems-polling-job") JobDetail jobDetail) {

    return TriggerBuilder.newTrigger()
        .forJob(jobDetail)
        .withIdentity("connected-systems-polling-trigger")
        .withSchedule(
            CronScheduleBuilder.cronSchedule(connectedSystemsPollingCronSchedule)
                .withMisfireHandlingInstructionFireAndProceed())
        .build();
  }
}
