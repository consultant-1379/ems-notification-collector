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
package com.ericsson.oss.adc.emsnc.it.util;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnmRemote;
import com.ericsson.oss.adc.emsnc.wiremock.SimulatedConnectedSystemsRemote;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public class RemoteSimulatorTest {
  private GenericContainer simulatorContainer;

  @SneakyThrows
  @Test
  public void testInteractingWithSimulator() {
    startSimulator();
    simulatorContainer.followOutput(new LogConsumer("emsnc"));

    await().atMost(30, TimeUnit.SECONDS).until(simulatorContainer::isHealthy);
    SimulatorProxyFactory simulatorProxyFactory = new SimulatorProxyFactory(simulatorContainer);
    SimulatedConnectedSystemsRemote simulatedConnectedSystems =
        simulatorProxyFactory.getSimulatedConnectedSystems();

    SimulatedEnmRemote simulatedEnm = simulatorProxyFactory.getSimulatedEnm(1);

    simulatedConnectedSystems.shutdown();
    simulatedConnectedSystems.start();

    assertEquals(0, simulatedEnm.getMaxEventCycles());
    simulatedEnm.setMaxEventCycles(1);
    assertEquals(1, simulatedEnm.getMaxEventCycles());
  }

  private void startSimulator() {
    log.info("Starting simulator");

    if (simulatorContainer == null) {
      simulatorContainer =
          new GenericContainer("emsnc-context-simulator:latest")
              .withEnv("ENM_HOST", "example")
              .withEnv("EVENT_CYCLES", "0");
      simulatorContainer.start();
    }
  }
}
