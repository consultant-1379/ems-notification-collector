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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import org.springframework.util.concurrent.ListenableFuture;

@Data
public class KafkaCollector<K, V> {
  private final MockKafkaUtil.TestKafkaTemplate<K, V> mockKafkaTemplate;

  private final List<V> collectedMessages = Collections.synchronizedList(new ArrayList<>());

  public KafkaCollector(MockKafkaUtil.TestKafkaTemplate<K, V> mockKafkaTemplate) {
    this.mockKafkaTemplate = mockKafkaTemplate;
  }

  public void start() {
    MockKafkaUtil.addStubForSend(
        mockKafkaTemplate,
        rec -> {
          collectedMessages.add(rec.value());
          // return value not used for now
          return mock(ListenableFuture.class);
        });
  }
}
