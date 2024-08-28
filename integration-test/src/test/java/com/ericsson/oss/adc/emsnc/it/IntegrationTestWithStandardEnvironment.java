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

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnmRemote;
import com.ericsson.oss.adc.emsnc.it.util.IntegrationTestBase;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Slf4j
public class IntegrationTestWithStandardEnvironment extends IntegrationTestBase {

  private static final int SCALED_UP_COUNT = 2;
  private static final int SCALED_DOWN_COUNT = 1;

  @BeforeEach
  public void setUp() {
    setUpStandardEnvironment();
  }

  @Test
  public void happyPathWithSingleEmsNcInstance() {
    assertEquals(0, dmaap.getMessageCount());

    SimulatedEnmRemote enm1 = simulatorProxyFactory.getSimulatedEnm(1);
    SimulatedEnmRemote enm2 = simulatorProxyFactory.getSimulatedEnm(2);

    enm1.addEventCycles(2);
    enm2.addEventCycles(2);
    waitForExactMessageCount(200);
  }

  @Disabled("TODO remove comment once test is stable")
  @Test
  public void emsncScaledDownDuringProcessing() {
    emsNcInstanceTarget(SCALED_UP_COUNT);

    assertEquals(0, dmaap.getMessageCount());

    SimulatedEnmRemote enm1 = simulatorProxyFactory.getSimulatedEnm(1);
    SimulatedEnmRemote enm2 = simulatorProxyFactory.getSimulatedEnm(2);

    enm1.addEventCycles(4);
    enm2.addEventCycles(4);

    await().atMost(45, TimeUnit.SECONDS).until(() -> enm1.getRemainingEventCycles() == 2);

    emsNcInstanceTarget(SCALED_DOWN_COUNT);

    waitForExactMessageCount(400);
  }

  @Test
  public void emsncScaledUpDuringProcessing() {
    emsNcInstanceTarget(SCALED_DOWN_COUNT);

    assertEquals(0, dmaap.getMessageCount());

    SimulatedEnmRemote enm1 = simulatorProxyFactory.getSimulatedEnm(1);
    SimulatedEnmRemote enm2 = simulatorProxyFactory.getSimulatedEnm(2);

    enm1.addEventCycles(4);
    enm2.addEventCycles(4);

    await().atMost(45, TimeUnit.SECONDS).until(() -> enm1.getRemainingEventCycles() == 2);

    emsNcInstanceTarget(SCALED_UP_COUNT);

    enm1.addEventCycles(2);
    enm2.addEventCycles(2);

    waitForExactMessageCount(600);
  }
}
