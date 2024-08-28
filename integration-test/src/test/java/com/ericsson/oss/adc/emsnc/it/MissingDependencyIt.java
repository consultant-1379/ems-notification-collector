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
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ericsson.oss.adc.emsnc.it.util.IntegrationTestBase;
import com.ericsson.oss.adc.emsnc.it.util.LogConsumer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class MissingDependencyIt extends IntegrationTestBase {
  @BeforeEach
  public void setUp() {
    setUpStandardEnvironment();
  }

  @Test
  void testConnectedSystemsNotStarted() throws InterruptedException {
    assertEquals(0, dmaap.getMessageCount());

    postgresContainer.stop();
    emsNcInstanceTarget(0);
    simulatorProxyFactory.getSimulatedConnectedSystems().shutdown();

    postgresContainer.start();
    emsNcInstanceTarget(1);

    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    await()
        .during(30, TimeUnit.SECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> emsncContainers.get(0).isHealthy());
    assertEquals(0, dmaap.getMessageCount());

    cs.start();
    waitForExactMessageCount(100);
  }

  @Test
  void eventPropagationContinuesWithConnectedSystemsDown() {

    emsNcInstanceTarget(1);

    assertEquals(0, dmaap.getMessageCount());

    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    await().atMost(300, TimeUnit.SECONDS).until(() -> checkRequiredMessagesReached(100));

    cs.shutdown();
    enm1.addEventCycles(2);
    enm2.addEventCycles(2);

    waitForExactMessageCount(300);
  }

  @Test
  void testEnmServerNotStarted() throws InterruptedException {
    emsNcInstanceTarget(0);
    enm1.shutdown();
    emsNcInstanceTarget(1);
    assertEquals(0, dmaap.getMessageCount());

    enm1.addEventCycles(1);
    await()
        .during(30, TimeUnit.SECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> emsncContainers.get(0).isHealthy());
    assertEquals(0, dmaap.getMessageCount());

    enm1.start();
    waitForExactMessageCount(50);
  }

  @Test
  public void eventPropagationContinuesWithEnmDown() {

    emsNcInstanceTarget(1);

    assertEquals(0, dmaap.getMessageCount());

    enm1.shutdown();
    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    await().atMost(300, TimeUnit.SECONDS).until(() -> checkRequiredMessagesReached(50));

    enm1.start();
    enm1.addEventCycles(2);
    enm2.addEventCycles(2);

    waitForExactMessageCount(300);
  }

  @Test
  void testEmsncRecoveryWhenDbDoesNotStart() {
    postgresContainer.stop();
    emsNcInstanceTarget(0);

    assertEquals(0, dmaap.getMessageCount());

    startEmsncExpectingToFail(
        new LogConsumer("emsnc", "Caused by: java.net.UnknownHostException: test-postgres"));
    await().atMost(300, TimeUnit.SECONDS).until(() -> !emsncContainers.get(0).isRunning());
    assertFalse(emsncContainers.get(0).isRunning());

    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    assertEquals(0, dmaap.getMessageCount());

    postgresContainer.start();
    emsNcInstanceTarget(1);

    waitForExactMessageCount(100);
  }

  @Test
  public void testEmsncRecoveryWhenDbGoesDown() {
    emsNcInstanceTarget(1);

    assertEquals(0, dmaap.getMessageCount());

    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    await().atMost(300, TimeUnit.SECONDS).until(() -> checkRequiredMessagesReached(100));

    postgresContainer
        .getDockerClient()
        .pauseContainerCmd(postgresContainer.getContainerId())
        .exec();
    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    await().atMost(150, TimeUnit.SECONDS).until(() -> !emsncContainers.get(0).isHealthy());

    postgresContainer
        .getDockerClient()
        .unpauseContainerCmd(postgresContainer.getContainerId())
        .exec();

    await().atMost(30, TimeUnit.SECONDS).until(() -> postgresContainer.isRunning());

    // restart container as k8s would
    //    emsNcInstanceTarget(0);
    //    emsNcInstanceTarget(1);
    //    await().atMost(150, TimeUnit.SECONDS).until(() -> emsncContainers.get(0).isHealthy());

    waitForExactMessageCount(200);
  }
}
