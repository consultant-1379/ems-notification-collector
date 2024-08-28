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
package com.ericsson.oss.adc.emsnc.integration.util;

import static org.mockito.Mockito.mock;

import com.ericsson.oss.adc.emsnc.processing.PollingTaskProcessor;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.util.concurrent.ListenableFuture;

public class KafkaPropagator<K, V extends PollingTask> {
  private final int testTopicCount;
  private final PollingTaskProcessor pollingTaskProcessor;
  private MockKafkaUtil.TestKafkaTemplate<K, V> mockKafkaTemplate;
  private Deque<ProducerRecord<K, V>> retryQueue = new ConcurrentLinkedDeque<>();

  public KafkaPropagator(
      // TODO create method to initialize stubs, too confusing in constructor
      MockKafkaUtil.TestKafkaTemplate<K, V> mockKafkaTemplate,
      int testTopicCount,
      PollingTaskProcessor pollingTaskProcessor) {
    this.mockKafkaTemplate = mockKafkaTemplate;
    this.testTopicCount = testTopicCount;
    this.pollingTaskProcessor = pollingTaskProcessor;
  }

  public void start() {
    MockKafkaUtil.addStubForSend(
        mockKafkaTemplate,
        rec -> {
          synchronized (this) {
            // TODO order is mixed up, partitions not simulated
            try {
              pollingTaskProcessor.receive(rec.value());
            } catch (Exception e) {
              // ignore, just add to the queue
              retryQueue.addLast(rec);
            }
            retryMissedMessages();
          }
          // return value not used for now
          return mock(ListenableFuture.class);
        });
  }

  private void retryMissedMessages() {
    // would be more realistic with a background thread / threads, but "good for now"
    boolean stillTrying = true;
    while (!retryQueue.isEmpty() && stillTrying) {
      ProducerRecord<K, V> record = retryQueue.removeFirst();
      try {
        pollingTaskProcessor.receive(record.value());
      } catch (Exception e) {
        // ignore, just add back to the queue
        retryQueue.addLast(record);
        stillTrying = false;
      }
    }
  }
}
