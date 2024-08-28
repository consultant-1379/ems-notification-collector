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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.mockito.stubbing.Answer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.util.concurrent.ListenableFuture;

public class MockKafkaUtil {

  private MockKafkaUtil() {
    // intentionally empty
  }

  public static <K, V> void addStubForSend(
      TestKafkaTemplate<K, V> mockKafkaTemplate, Receiver<K, V> receiver) {
    assertTrue(mockingDetails(mockKafkaTemplate).isMock());
    when(mockKafkaTemplate.send(any(Message.class))).thenCallRealMethod();
    when(mockKafkaTemplate.send(anyString(), any())).thenCallRealMethod();
    when(mockKafkaTemplate.send(any(ProducerRecord.class))).thenCallRealMethod();
    when(mockKafkaTemplate.send(anyString(), any(), any())).thenCallRealMethod();
    when(mockKafkaTemplate.send(anyString(), anyInt(), any(), any())).thenCallRealMethod();
    when(mockKafkaTemplate.send(anyString(), anyInt(), anyLong(), any(), any()))
        .thenCallRealMethod();
    when(mockKafkaTemplate.doSend(any(ProducerRecord.class)))
        .thenAnswer(
            (Answer<ListenableFuture<SendResult<K, V>>>)
                invocationOnMock -> receiver.receive(invocationOnMock.getArgument(0)));
  }

  @FunctionalInterface
  public static interface Receiver<K, V> {
    ListenableFuture<SendResult<K, V>> receive(ProducerRecord<K, V> record);
  }

  public abstract class TestKafkaTemplate<K, V> extends KafkaTemplate<K, V> {
    public TestKafkaTemplate(ProducerFactory<K, V> producerFactory) {
      super(producerFactory);
    }

    @Override
    protected ListenableFuture<SendResult<K, V>> doSend(ProducerRecord<K, V> producerRecord) {
      return super.doSend(producerRecord);
    }
  }
}
