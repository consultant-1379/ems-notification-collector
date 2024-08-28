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
package com.ericsson.oss.adc.emsnc.k8s.performance;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnmRemote;
import com.ericsson.oss.adc.emsnc.it.kafka.DmaapKafkaRemote;
import com.ericsson.oss.adc.emsnc.it.util.SimulatorProxyFactory;
import com.ericsson.oss.adc.emsnc.k8s.util.K8sTestBase;
import io.kubernetes.client.openapi.models.V1PodList;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.AfterClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
public class K8sPerformanceTest extends K8sTestBase {

  private static int CYCLE_LENGTH = 30;
  private static int DEFAULT_TARGET_INSTANCE_COUNT_EVENT_HEAVY = 5;
  private static int DEFAULT_TARGET_INSTANCE_COUNT_TARGET_HEAVY = 2500;
  private static int DEFAULT_EVENTS_BY_TARGET_EVENT_HEAVY = 500;
  private static int DEFAULT_EVENTS_BY_TARGET_TARGET_HEAVY = 1;
  private static int ENM_COUNT = 2;
  private static int NE_TYPE_COUNT = 2;
  private static double MULTIPLIER = 1.5;
  private static int BURST_SCENARION_MULTIPLIER = 2;

  V1PodList podList = getPodListFromCluster();
  String contextSimCurrentPodName = getCurrentPodName(podList, CONTEXT_SIM_POD_NAME);
  SimulatorProxyFactory simulatorProxyFactory = new SimulatorProxyFactory(contextSimCurrentPodName);
  SimulatedEnmRemote enm1 = simulatorProxyFactory.getSimulatedEnm(1);
  SimulatedEnmRemote enm2 = simulatorProxyFactory.getSimulatedEnm(2);
  DmaapKafkaRemote dmaap = simulatorProxyFactory.getDmaapKafkaClient();

  @AfterClass
  void resetEmsncScale() {
    scaleDeployment(EMSNC_DEPLOYMENT_NAME, 1, 240);
  }

  @SneakyThrows
  @Test
  @DisplayName("Measure Largest Successful Target Heavy Processing")
  void targetHeavyMaxProcessableEventTest() {

    scaleDeployment(EMSNC_DEPLOYMENT_NAME, 1, 240);

    int eventsGeneratedByTargetPerCycle = DEFAULT_EVENTS_BY_TARGET_TARGET_HEAVY;
    int currentTargetInstanceCount = DEFAULT_TARGET_INSTANCE_COUNT_TARGET_HEAVY;
    int lastSuccessfulTargetInstanceCount = 0;

    while (areCurrentParametersProcessable(
        currentTargetInstanceCount, eventsGeneratedByTargetPerCycle)) {
      lastSuccessfulTargetInstanceCount = currentTargetInstanceCount;
      currentTargetInstanceCount = (int) (currentTargetInstanceCount * MULTIPLIER);
    }

    int processedEventCount =
        lastSuccessfulTargetInstanceCount
            * eventsGeneratedByTargetPerCycle
            * NE_TYPE_COUNT
            * ENM_COUNT;

    log.info("Largest processable target Instance Count: " + processedEventCount);

    int eventCountFromLeftoverCycle =
        currentTargetInstanceCount * eventsGeneratedByTargetPerCycle * ENM_COUNT * NE_TYPE_COUNT;
    log.info("Leftover events to process: {}", eventCountFromLeftoverCycle);
    waitForEventProcessing(eventCountFromLeftoverCycle, CYCLE_LENGTH);
  }

  @SneakyThrows
  @Test
  @DisplayName("Measure Largest Successful Target Heavy Processing By Multiple Instances")
  void targetHeavyMaxProcessableEventTestParallel() {

    scaleDeployment(EMSNC_DEPLOYMENT_NAME, 2, 240);

    int eventsGeneratedByTargetPerCycle = DEFAULT_EVENTS_BY_TARGET_TARGET_HEAVY;
    int currentTargetInstanceCount = DEFAULT_TARGET_INSTANCE_COUNT_TARGET_HEAVY;
    int lastSuccessfulTargetInstanceCount = 0;

    while (areCurrentParametersProcessable(
        currentTargetInstanceCount, eventsGeneratedByTargetPerCycle)) {
      lastSuccessfulTargetInstanceCount = currentTargetInstanceCount;
      currentTargetInstanceCount = (int) (currentTargetInstanceCount * MULTIPLIER);
    }

    int processedEventCount =
        lastSuccessfulTargetInstanceCount
            * eventsGeneratedByTargetPerCycle
            * NE_TYPE_COUNT
            * ENM_COUNT;
    log.info("Largest processable target Instance Count: " + processedEventCount);

    int eventCountFromLeftoverCycle =
        currentTargetInstanceCount * eventsGeneratedByTargetPerCycle * ENM_COUNT * NE_TYPE_COUNT;
    log.info("Leftover events to process: {}", eventCountFromLeftoverCycle);
    waitForEventProcessing(eventCountFromLeftoverCycle, CYCLE_LENGTH);
  }

  @SneakyThrows
  @Test
  @DisplayName("Measure Largest Successful Event Heavy Processing & Burst Scenario")
  void eventHeavyMaxProcessableEventTest() {

    scaleDeployment(EMSNC_DEPLOYMENT_NAME, 1, 240);

    int eventsGeneratedByTargetPerCycle = DEFAULT_EVENTS_BY_TARGET_EVENT_HEAVY;
    int lastSuccessfulEventTarget = 0;

    while (areCurrentParametersProcessable(
        DEFAULT_TARGET_INSTANCE_COUNT_EVENT_HEAVY, eventsGeneratedByTargetPerCycle)) {
      lastSuccessfulEventTarget = eventsGeneratedByTargetPerCycle;
      eventsGeneratedByTargetPerCycle = (int) (eventsGeneratedByTargetPerCycle * MULTIPLIER);
    }

    int processedEventCount =
        DEFAULT_TARGET_INSTANCE_COUNT_EVENT_HEAVY
            * lastSuccessfulEventTarget
            * ENM_COUNT
            * NE_TYPE_COUNT;

    log.info(
        "Largest total amount of processed events during 1 cycle with a single EMS adapter instance: {}",
        processedEventCount);

    // Waiting for previous events to be processed
    int eventCountFromLeftoverCycle =
        DEFAULT_TARGET_INSTANCE_COUNT_EVENT_HEAVY
            * eventsGeneratedByTargetPerCycle
            * ENM_COUNT
            * NE_TYPE_COUNT;
    log.info("Leftover events to process: {}", eventCountFromLeftoverCycle);
    waitForEventProcessing(eventCountFromLeftoverCycle, CYCLE_LENGTH);

    // Burst scenario
    int eventsGeneratedByTargetBurstScenario =
        lastSuccessfulEventTarget * BURST_SCENARION_MULTIPLIER;

    int expectedEventCount =
        DEFAULT_TARGET_INSTANCE_COUNT_EVENT_HEAVY
            * NE_TYPE_COUNT
            * eventsGeneratedByTargetBurstScenario
            * ENM_COUNT;
    log.info(
        "Burst scenario: Generating events with {} targetInstanceCount, {} eventsGeneratedByTagerget, {} total event count",
        DEFAULT_TARGET_INSTANCE_COUNT_EVENT_HEAVY,
        eventsGeneratedByTargetBurstScenario,
        expectedEventCount);
    areCurrentParametersProcessable(
        DEFAULT_TARGET_INSTANCE_COUNT_EVENT_HEAVY, eventsGeneratedByTargetBurstScenario);
    waitForEventProcessing(expectedEventCount, CYCLE_LENGTH * BURST_SCENARION_MULTIPLIER);
  }

  @SneakyThrows
  @Test
  @DisplayName("Measure Largest Successful Event Heavy Processing By Multiple Instances")
  void eventHeavyMaxProcessableEventTestParallel() {

    scaleDeployment(EMSNC_DEPLOYMENT_NAME, 2, 240);

    int eventsGeneratedByTargetPerCycle = DEFAULT_EVENTS_BY_TARGET_EVENT_HEAVY;
    int lastSuccessfulEventTarget = 0;

    while (areCurrentParametersProcessable(
        DEFAULT_TARGET_INSTANCE_COUNT_EVENT_HEAVY, eventsGeneratedByTargetPerCycle)) {
      lastSuccessfulEventTarget = eventsGeneratedByTargetPerCycle;
      eventsGeneratedByTargetPerCycle = (int) (eventsGeneratedByTargetPerCycle * MULTIPLIER);
    }
    int eventCountFromLeftoverCycle =
        DEFAULT_TARGET_INSTANCE_COUNT_EVENT_HEAVY
            * eventsGeneratedByTargetPerCycle
            * ENM_COUNT
            * NE_TYPE_COUNT;
    log.info("Leftover events to process: {}", eventCountFromLeftoverCycle);
    waitForEventProcessing(eventCountFromLeftoverCycle, CYCLE_LENGTH);

    int processedEventCount =
        DEFAULT_TARGET_INSTANCE_COUNT_EVENT_HEAVY
            * lastSuccessfulEventTarget
            * ENM_COUNT
            * NE_TYPE_COUNT;
    log.info(
        "Largest total amount of processed events during 1 cycle with multiple EMS adapter instance: {}",
        processedEventCount);
  }

  private boolean areCurrentParametersProcessable(
      int targetInstanceCount, int eventsGeneratedByTargetPerCycle) {

    dmaap.reset();
    assertEquals(0, dmaap.getMessageCount());
    int expectedEventCount =
        targetInstanceCount * NE_TYPE_COUNT * eventsGeneratedByTargetPerCycle * ENM_COUNT;
    log.info(
        "Generating events with {} targetInstanceCount, {} eventsGeneratedByTagerget, {} total event count",
        targetInstanceCount,
        eventsGeneratedByTargetPerCycle,
        expectedEventCount);

    enm1.setTargetInstanceCount(targetInstanceCount);
    enm2.setTargetInstanceCount(targetInstanceCount);

    enm1.setEventsPerCycle(eventsGeneratedByTargetPerCycle);
    enm2.setEventsPerCycle(eventsGeneratedByTargetPerCycle);

    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    // EMS Polling Jobs are scheduled to start at '00 & '30
    await().atMost(30, TimeUnit.SECONDS).until(() -> LocalDateTime.now().getSecond() % 30 == 0);

    try {
      waitForEventProcessing(expectedEventCount, CYCLE_LENGTH);
    } catch (ConditionTimeoutException e) {
      return false;
    }
    return true;
  }

  private void waitForEventProcessing(int expectedEventCount, int timeOut) {
    log.info("EMS Polling Cycle starting");
    await()
        .atMost(timeOut, TimeUnit.SECONDS)
        .pollInterval(5, TimeUnit.SECONDS)
        .until(
            () -> {
              return checkRequiredMessagesReached(expectedEventCount, dmaap);
            });
  }
}
