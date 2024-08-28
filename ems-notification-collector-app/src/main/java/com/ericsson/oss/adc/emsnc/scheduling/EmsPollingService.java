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

import com.ericsson.oss.adc.emsnc.controller.health.HealthCheck;
import com.ericsson.oss.adc.emsnc.controller.health.HealthCheckError;
import com.ericsson.oss.adc.emsnc.processing.TargetResolvingService;
import com.ericsson.oss.adc.emsnc.processing.data.EnmInfo;
import com.ericsson.oss.adc.emsnc.processing.data.PollingSubscription;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import com.ericsson.oss.adc.emsnc.service.kafka.KafkaTopicHashService;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFuture;

@Service
@Slf4j
public class EmsPollingService {
  @Autowired
  @Qualifier("emsnc-kafka-template")
  private KafkaTemplate<String, PollingTask> kafkaTemplate;

  @Value("${emsnc.timing.polling-offset-seconds}")
  private int pollDelay;

  @Value("${emsnc.kafka.emsnc-internal.topic-count}")
  private int topicCount;

  @Value("${emsnc.timing.polling-period-seconds}")
  private int pollingPeriodSeconds;

  @Value("${emsnc.kafka.emsnc-internal.send-timeout}")
  private long sendTimeout;

  @Autowired private TargetResolvingService targetResolvingService;

  @Autowired private KafkaTopicHashService kafkaTopicHashService;

  @Autowired private HealthCheck healthCheck;

  @Transactional
  public void doEmsPolling(
      String jobKeyName,
      String jobKeyGroup,
      Date scheduledFireTime,
      Date actualFireTime,
      Map<String, Object> jobDataMap)
      throws InterruptedException, ExecutionException, TimeoutException {
    SimpleDateFormat df = new SimpleDateFormat("hh:mm:ss");
    long jobStartTimestamp = scheduledFireTime.getTime();
    EnmInfo enmInfo = (EnmInfo) jobDataMap.get(ConnectedSystemsPollingJob.JDM_KEY_ENM_INFO);
    PollingSubscription subscription =
        (PollingSubscription) jobDataMap.get(ConnectedSystemsPollingJob.JDM_KEY_SUBSCRIPTION);

    log.debug(
        "Generating polling tasks for '{}' at '{}' (scheduled: {})",
        subscription.getNeType(),
        enmInfo.getName(),
        df.format(actualFireTime),
        df.format(scheduledFireTime));

    // build a map of topic names -> list of belonging target names
    List<String> targetNames =
        targetResolvingService.findTargetNames(enmInfo, subscription.getNeType());
    for (Map.Entry<String, List<String>> entry :
        kafkaTopicHashService.distributeTopicNames(targetNames, topicCount).entrySet()) {
      String topicName = entry.getKey();
      List<String> targetList = entry.getValue();
      log.debug("Publishing total {} targets to {}", targetList.size(), topicName);
      for (List<String> targetBatch : kafkaTopicHashService.splitListToBatches(targetList)) {
        long startTimestamp = jobStartTimestamp - (pollDelay + pollingPeriodSeconds) * 1000;
        log.debug(
            "Publishing batch of {} targets to topic {} ({}), startTimeStamp: {}",
            targetBatch.size(),
            topicName,
            targetBatch,
            startTimestamp);

        PollingTask currentPollingTask =
            PollingTask.builder()
                .startTimestamp(startTimestamp)
                .endTimestamp(jobStartTimestamp - pollDelay * 1000)
                .jobKeyName(jobKeyName)
                .jobKeyGroup(jobKeyGroup)
                .targetNames(targetBatch)
                .neType(subscription.getNeType())
                .build();
        log.debug("Sending message ({}) to '{}'", df.format(scheduledFireTime), topicName);
        String messageKey = generateMessageKey(currentPollingTask);
        try {
          ListenableFuture<SendResult<String, PollingTask>> sendFuture =
              kafkaTemplate.send(
                  topicName, enmInfo.getAssignedPartition(), messageKey, currentPollingTask);
          sendFuture.get(sendTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException
            | ExecutionException
            | TimeoutException
            | RuntimeException e) {
          log.error("Sending message to " + topicName + " failed", e);
          healthCheck.failHealthCheck(
              HealthCheckError.builder()
                  .message(HealthCheckError.Message.SEND_FAILED)
                  .parameter(topicName)
                  .parameter(e.getClass().getName())
                  .build());
          // rethrow to let the job re-execute as well
          throw e;
        }
      }
    }
  }

  private String generateMessageKey(PollingTask currentPollingTask) {
    return String.valueOf(
        UUID.nameUUIDFromBytes(
            String.valueOf(currentPollingTask).getBytes(StandardCharsets.UTF_8)));
  }
}
