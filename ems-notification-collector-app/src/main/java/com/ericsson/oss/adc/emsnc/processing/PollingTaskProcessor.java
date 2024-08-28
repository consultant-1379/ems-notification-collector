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
package com.ericsson.oss.adc.emsnc.processing;

import com.ericsson.oss.adc.emsnc.client.enm.EmsCredentials;
import com.ericsson.oss.adc.emsnc.client.enm.EnmClientService;
import com.ericsson.oss.adc.emsnc.client.enm.LoginHandlingEnmClient;
import com.ericsson.oss.adc.emsnc.client.enm.model.EnmEventsResponse;
import com.ericsson.oss.adc.emsnc.client.enm.model.Event;
import com.ericsson.oss.adc.emsnc.client.enm.model.FilterClause;
import com.ericsson.oss.adc.emsnc.client.enm.model.Operator;
import com.ericsson.oss.adc.emsnc.controller.health.HealthCheck;
import com.ericsson.oss.adc.emsnc.controller.health.HealthCheckError;
import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.processing.data.EnmInfo;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import com.ericsson.oss.adc.emsnc.scheduling.ConnectedSystemsPollingJob;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;

@SuppressWarnings("java:S2142") // intentionally not interrupting the thread
@Service
@Slf4j
@RequiredArgsConstructor
public class PollingTaskProcessor {

  @Qualifier("dmaap-kafka-template")
  private final KafkaTemplate<String, YangEvent> kafkaTemplate;

  @Value("${emsnc.kafka.dmaap.propagation-topic-name}")
  private String dmaapTopicName;

  @Value("${emsnc.kafka.dmaap.send-timeout}")
  private long sendTimeout;

  private final EnmClientService enmClientService;
  private final SchedulerFactoryBean schedulerFactoryBean;
  private final HealthCheck healthCheck;

  // TODO [IDUN-1890] increase processing thread count
  public void receive(PollingTask payload) {
    log.debug(
        "Polling {} targets ({}, startTimestamp: {}) and propagating to {}",
        payload.getTargetNames().size(),
        payload.getTargetNames(),
        payload.getStartTimestamp(),
        dmaapTopicName);

    Scheduler scheduler = schedulerFactoryBean.getScheduler();

    try {
      JobKey jobKey = new JobKey(payload.getJobKeyName(), payload.getJobKeyGroup());
      if (scheduler.getJobDetail(jobKey) == null) {
        log.warn("There is no ENM to poll, discarding task.");
        return;
      }
      JobDataMap jobDataMap = scheduler.getJobDetail(jobKey).getJobDataMap();

      EnmInfo enmInfo = (EnmInfo) jobDataMap.get(ConnectedSystemsPollingJob.JDM_KEY_ENM_INFO);

      if (enmInfo == null) {
        log.warn("There is no ENM to poll, discarding task.");
      } else {
        EmsCredentials emsCredentials = enmInfo.toEmsCredentials();
        LoginHandlingEnmClient enmClient = enmClientService.getEnmClient(emsCredentials);

        String startTimestamp = Instant.ofEpochMilli(payload.getStartTimestamp()).toString();
        String endTimestamp = Instant.ofEpochMilli(payload.getEndTimestamp()).toString();

        List<FilterClause> filterClauses =
            createFilterClauses(startTimestamp, endTimestamp, payload.getTargetNames());
        EnmEventsResponse result =
            enmClient.getVnfConfigEvents(
                FilterClause.EVENT_DETECTION_TIMESTAMP + " asc", new Gson().toJson(filterClauses));

        log.debug(
            "Propagating {} events to {} (targets {}, transactional: {})",
            result.getEvents().size(),
            dmaapTopicName,
            payload.getTargetNames(),
            kafkaTemplate.isTransactional());
        for (Event event : result.getEvents()) {
          sendEventToDmaap(payload, enmInfo, event);
        }
      }
    } catch (SchedulerException e) {
      log.warn("Scheduler can not return EMS Polling Job", e);
    }
  }

  private void sendEventToDmaap(PollingTask payload, EnmInfo enmInfo, Event event) {
    try {
      ListenableFuture<SendResult<String, YangEvent>> sendFuture =
          kafkaTemplate.send(
              dmaapTopicName,
              EventMapperUtils.cmToYang(event, enmInfo.getName(), payload.getNeType()));
      sendFuture.get(sendTimeout, TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException | RuntimeException e) {
      log.error("Sending message to " + dmaapTopicName + " failed", e);
      healthCheck.failHealthCheck(
          HealthCheckError.builder()
              .message(HealthCheckError.Message.SEND_FAILED)
              .parameter(dmaapTopicName)
              .parameter(e.getClass().getName())
              .build());
    }
  }

  private static List<FilterClause> createFilterClauses(
      String fromTimestamp, String toTimestamp, List<String> targetNames) {
    final List<FilterClause> clauses = new ArrayList<>();
    FilterClause filterClause =
        FilterClause.builder()
            .attrName(FilterClause.EVENT_RECORD_TIMESTAMP)
            .operator(Operator.GTE)
            .attrValue(fromTimestamp)
            .build();
    clauses.add(filterClause);
    filterClause =
        FilterClause.builder()
            .attrName(FilterClause.EVENT_RECORD_TIMESTAMP)
            .operator(Operator.LT)
            .attrValue(toTimestamp)
            .build();
    clauses.add(filterClause);
    filterClause =
        FilterClause.builder()
            .attrName(FilterClause.TARGET_NAME)
            .operator(Operator.EQ)
            .attrValues(targetNames)
            .build();
    clauses.add(filterClause);
    return clauses;
  }
}
