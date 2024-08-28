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
package com.ericsson.oss.adc.emsnc.service.kafka.listener;

import com.ericsson.oss.adc.emsnc.processing.PollingTaskProcessor;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.support.Acknowledgment;

@RequiredArgsConstructor
@Slf4j
public class AcknowledgingTaskListener
    implements AcknowledgingMessageListener<String, PollingTask> {

  final PollingTaskProcessor pollingTaskProcessor;

  @Override
  public void onMessage(ConsumerRecord<String, PollingTask> data, Acknowledgment acknowledgment) {
    try {
      pollingTaskProcessor.receive(data.value());
      // commit offset
      acknowledgment.acknowledge();
    } catch (Exception e) {
      log.error("Failed to process {}", data.key(), e);
      // TODO what is a reasonable timeout?
      acknowledgment.nack(5000);
    }
  }
}
