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
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import com.ericsson.oss.adc.emsnc.integration.util.KafkaCollector;
import com.ericsson.oss.adc.emsnc.integration.util.KafkaPropagator;
import com.ericsson.oss.adc.emsnc.integration.util.MockKafkaUtil;
import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.processing.PollingTaskProcessor;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import com.ericsson.oss.adc.emsnc.wiremock.SimulatedConnectedSystems;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@Slf4j
@SpringBootTest
@ActiveProfiles({"test"})
@TestPropertySource(
    properties = {
      "emsnc.kafka.emsnc-internal.topic-count=" + EventPropagationMultipleEnmsTest.TEST_TOPIC_COUNT,
      "emsnc.timing.polling-period-seconds=5",
      "emsnc.timing.ems-polling-cron-schedule=0/5 * * * * ?",
      "emsnc.timing.connected-systems-polling-cron-schedule=0/5 * * * * ?",
      "emsnc.client.connected-systems.port=#{simulatedConnectedSystems.wireMockServer.port}"
    })
public class EventPropagationMultipleEnmsTest {
  @TestConfiguration
  public static class SimulationConfiguration {
    @Bean(name = "enm-1", destroyMethod = "shutdown")
    public SimulatedEnm simulatedEnm1() {
      Map<String, Integer> targets = new HashMap();
      targets.put("RadioNode", 5);
      targets.put("Router6675", 5);
      return SimulatedEnm.builder().targets(targets).maxEventCycles(0).build().start();
    }

    @Bean(name = "enm-2", destroyMethod = "shutdown")
    public SimulatedEnm simulatedEnm2() {
      Map<String, Integer> targets = new HashMap();
      targets.put("RadioNode", 5);
      targets.put("Router6675", 5);
      return SimulatedEnm.builder().targets(targets).maxEventCycles(0).build().start();
    }

    @Bean(destroyMethod = "shutdown")
    public SimulatedConnectedSystems simulatedConnectedSystems(
        @Qualifier("enm-1") SimulatedEnm simulatedEnm1,
        @Qualifier("enm-2") SimulatedEnm simulatedEnm2) {
      log.info("Using port {} for simulated Connected Systems");
      return SimulatedConnectedSystems.builder()
          .enm(simulatedEnm1)
          .enm(simulatedEnm2)
          .build()
          .start();
    }
  }

  public static final int TEST_TOPIC_COUNT = 3;

  @MockBean(name = "dmaap-kafka-template")
  MockKafkaUtil.TestKafkaTemplate<String, YangEvent> dmaapKafka;

  @MockBean(name = "emsnc-kafka-template")
  MockKafkaUtil.TestKafkaTemplate<String, PollingTask> emsncKafka;

  @Autowired private PollingTaskProcessor pollingTaskProcessor;

  @Autowired
  @Qualifier("enm-1")
  private SimulatedEnm simulatedEnm1;

  @Autowired
  @Qualifier("enm-2")
  private SimulatedEnm simulatedEnm2;

  @Autowired private SimulatedConnectedSystems simulatedConnectedSystems;

  private KafkaCollector dmaapKafkaCollector;
  private SimulatedConnectedSystems simulatedCs;
  private KafkaPropagator internalKafkaPropagator;

  @BeforeEach
  public void setUp() {
    dmaapKafkaCollector = new KafkaCollector(dmaapKafka);
    internalKafkaPropagator =
        new KafkaPropagator(emsncKafka, TEST_TOPIC_COUNT, pollingTaskProcessor);
    dmaapKafkaCollector.start();
    internalKafkaPropagator.start();
  }

  @SneakyThrows
  @Test
  public void testBasicEventPropagationWith2Enms() {
    simulatedEnm1.setMaxEventCycles(1);
    simulatedEnm2.setMaxEventCycles(1);
    await()
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> dmaapKafkaCollector.getCollectedMessages().size() == 200);

    assertEquals(200, dmaapKafkaCollector.getCollectedMessages().size());
  }
}
