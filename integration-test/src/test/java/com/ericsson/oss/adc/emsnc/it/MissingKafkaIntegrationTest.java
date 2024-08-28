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
package com.ericsson.oss.adc.emsnc.it;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ericsson.oss.adc.emsnc.it.util.IntegrationTestBase;
import com.ericsson.oss.adc.emsnc.it.util.LogConsumer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
public class MissingKafkaIntegrationTest extends IntegrationTestBase {

  @BeforeEach
  public void setUp() {
    assurePostgresState(true);
    assureContextSimulatorRunning();

    enm1.reset(DEFAULT_ENM_EVENTS_PER_CYCLE);
    enm2.reset(DEFAULT_ENM_EVENTS_PER_CYCLE);
    cs.reset();
    dmaap.reset();
  }

  @Test
  public void testEMSNCKafkaFailedEmsncNeedsRestart() {
    assureDmaapKafkaState(true);
    assureEmsncKafkaState(true);
    emsNcInstanceTarget(1);

    emsncKafkaContainer
        .getDockerClient()
        .pauseContainerCmd(emsncKafkaContainer.getContainerId())
        .exec();
    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    await().atMost(400, TimeUnit.SECONDS).until(() -> !emsncContainers.get(0).isHealthy());

    emsncKafkaContainer
        .getDockerClient()
        .unpauseContainerCmd(emsncKafkaContainer.getContainerId())
        .exec();

    // restart container as k8s would
    //    emsNcInstanceTarget(0);
    //    emsNcInstanceTarget(1);

    waitForExactMessageCount(100);
  }

  @Test
  public void testEMSNCKafkaNotStarted() {
    emsNcInstanceTarget(0);
    assureEmsncKafkaState(false);
    assureDmaapKafkaState(true);
    startEmsncExpectingToFail(
        new LogConsumer(
            "emsnc",
            "org.apache.kafka.common.config.ConfigException: No resolvable bootstrap urls given in bootstrap.servers"));

    emsNcInstanceTarget(0);
    assureEmsncKafkaState(true);
    // now that the internal Kafka is running, EMSNC should start normally
    emsNcInstanceTarget(1);

    assertEquals(0, dmaap.getMessageCount());

    enm1.addEventCycles(2);
    enm2.addEventCycles(2);

    waitForExactMessageCount(200);
  }

  @Test
  public void testDmaapKafkaNotStarted() {
    emsNcInstanceTarget(0);
    assureDmaapKafkaState(false);
    assureEmsncKafkaState(true);
    startEmsncExpectingToFail(
        new LogConsumer(
            "emsnc",
            "org.apache.kafka.common.config.ConfigException: No resolvable bootstrap urls given in bootstrap.servers"));

    emsNcInstanceTarget(0);
    assureDmaapKafkaState(true);
    emsNcInstanceTarget(1);

    assertEquals(0, dmaap.getMessageCount());

    enm1.addEventCycles(2);
    enm2.addEventCycles(2);

    waitForExactMessageCount(200);
  }

  @Test
  public void testDmaapKafkaFailedEmsncNeedsRestart() {
    assureDmaapKafkaState(true);
    assureEmsncKafkaState(true);
    emsNcInstanceTarget(1);

    dmaapKafkaContainer
        .getDockerClient()
        .pauseContainerCmd(dmaapKafkaContainer.getContainerId())
        .exec();
    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    await().atMost(300, TimeUnit.SECONDS).until(() -> !emsncContainers.get(0).isHealthy());

    dmaapKafkaContainer
        .getDockerClient()
        .unpauseContainerCmd(dmaapKafkaContainer.getContainerId())
        .exec();

    await().atMost(30, TimeUnit.SECONDS).until(() -> dmaapKafkaContainer.isRunning());

    // restart container as k8s would
    // emsNcInstanceTarget(0);
    // emsNcInstanceTarget(1);

    waitForExactMessageCount(100);
  }
}
