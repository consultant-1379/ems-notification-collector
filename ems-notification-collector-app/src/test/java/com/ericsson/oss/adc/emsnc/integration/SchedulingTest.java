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
package com.ericsson.oss.adc.emsnc.integration;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import com.ericsson.oss.adc.emsnc.integration.util.KafkaCollector;
import com.ericsson.oss.adc.emsnc.integration.util.KafkaPropagator;
import com.ericsson.oss.adc.emsnc.integration.util.MockKafkaUtil;
import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.processing.PollingTaskProcessor;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import com.ericsson.oss.adc.emsnc.wiremock.SimulatedConnectedSystems;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.concurrent.ListenableFuture;

@Slf4j
@SpringBootTest
@ActiveProfiles({"test"})
@TestPropertySource(
    properties = {
      "emsnc.kafka.emsnc-internal.topic-count=" + SchedulingTest.TEST_TOPIC_COUNT,
      "emsnc.timing.polling-period-seconds=5",
      "emsnc.timing.ems-polling-cron-schedule=0/5 * * * * ?",
      "emsnc.timing.connected-systems-polling-cron-schedule=0/5 * * * * ?",
      "emsnc.client.connected-systems.port=#{simulatedConnectedSystems.wireMockServer.port}"
    })
public class SchedulingTest {
  @TestConfiguration
  public static class SimulationConfiguration {
    @Bean(destroyMethod = "shutdown")
    public SimulatedEnm simulatedEnm() {
      Map<String, Integer> targets = new HashMap();
      targets.put("RadioNode", 1);
      targets.put("Router6675", 1);
      return SimulatedEnm.builder().targets(targets).maxEventCycles(2).build().start();
    }

    @Bean(destroyMethod = "shutdown")
    public SimulatedConnectedSystems simulatedConnectedSystems(SimulatedEnm simulatedEnm) {
      log.info("Using port {} for simulated Connected Systems");
      return SimulatedConnectedSystems.builder().enm(simulatedEnm).build().start();
    }
  }

  public static final int TEST_TOPIC_COUNT = 3;

  @MockBean(name = "dmaap-kafka-template")
  MockKafkaUtil.TestKafkaTemplate<String, YangEvent> dmaapKafka;

  @MockBean(name = "emsnc-kafka-template")
  MockKafkaUtil.TestKafkaTemplate<String, PollingTask> emsncKafka;

  @Autowired private PollingTaskProcessor pollingTaskProcessor;

  @Autowired private SimulatedEnm simulatedEnm;

  @Autowired private SimulatedConnectedSystems simulatedConnectedSystems;

  private KafkaCollector dmaapKafkaCollector;
  private SimulatedConnectedSystems simulatedCs;
  private KafkaPropagator internalKafkaPropagator;
  private Set<ProducerRecord> records = new HashSet<>();
  private int successfulMessages = 0;

  @BeforeEach
  public void setUp() {
    MockKafkaUtil.addStubForSend(
        emsncKafka,
        rec -> {
          // Only returns successfully if the exact message is tried for the second time.
          if (!records.contains(rec)) {
            records.add(rec);
            throw new RuntimeException();
          }
          successfulMessages++;
          return mock(ListenableFuture.class);
        });
  }

  @SneakyThrows
  @Test
  public void testRetryingFailedQuartzTask() {
    await().atMost(60, TimeUnit.SECONDS).until(() -> successfulMessages > 0);
  }
}
