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
package com.ericsson.oss.adc.emsnc.k8s.robustness;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnmRemote;
import com.ericsson.oss.adc.emsnc.it.kafka.DmaapKafkaRemote;
import com.ericsson.oss.adc.emsnc.it.util.SimulatorProxyFactory;
import com.ericsson.oss.adc.emsnc.k8s.util.K8sExecUtil;
import com.ericsson.oss.adc.emsnc.k8s.util.K8sTestBase;
import io.kubernetes.client.Metrics;
import io.kubernetes.client.custom.ContainerMetrics;
import io.kubernetes.client.custom.NodeMetrics;
import io.kubernetes.client.custom.NodeMetricsList;
import io.kubernetes.client.custom.PodMetrics;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.util.Config;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
class K8sRobustnessTest extends K8sTestBase {

  private SimulatedEnmRemote enm1;
  private SimulatedEnmRemote enm2;
  private DmaapKafkaRemote dmaap;

  @SneakyThrows
  @Test
  @DisplayName("TC06 - Test scaling Kafka")
  void testKafkaScaleDownAndUp() {
    // Stopping kafka instances
    scaleStateFulSet(KAFKA_STATEFUL_SET_NAME, 0, 120);

    // Wait for terminating pods to disappear
    waitForPodState(180, 0, KAFKA_POD_NAME);
    log.info("Scale down of {} successful", KAFKA_STATEFUL_SET_NAME);

    // Starting kafka instances. Note: 3 is the minimum for Kafka, otherwise it will flush errors
    scaleStateFulSet(KAFKA_STATEFUL_SET_NAME, 3, 120);
    log.info("Scale up of {} successful", KAFKA_STATEFUL_SET_NAME);

    // Verify kafka instances are ready
    V1StatefulSet statefulSet =
        appApi.readNamespacedStatefulSet(KAFKA_STATEFUL_SET_NAME, namespace, null, null, null);
    assertEquals(3, statefulSet.getStatus().getReadyReplicas());
  }

  @DisplayName("Test all PODs are Running")
  @AfterEach
  void verifyAllPodsAreInRunningState() {
    V1PodList list = getPodListFromCluster();
    log.info("List of pods in Running state:");
    for (V1Pod item : list.getItems()) {
      log.info(Objects.requireNonNull(item.getMetadata()).getName());
      assertEquals("Running", Objects.requireNonNull(item.getStatus()).getPhase());
    }
  }

  @SneakyThrows
  @Test
  @DisplayName("TC02 - Test scaling EMSNC")
  void testScaleEmsnc() {
    // Scale down EMSNC
    scaleDeployment(EMSNC_DEPLOYMENT_NAME, 0, 180);

    // Wait for terminating pods to disappear
    waitForPodState(180, 0, EMSNC_POD_NAME);
    log.info("Scale down successful");

    // Scale up EMSNC
    scaleDeployment(EMSNC_DEPLOYMENT_NAME, 1, 180);
    V1Deployment deployment =
        appApi.readNamespacedDeployment(EMSNC_DEPLOYMENT_NAME, namespace, null, null, null);
    log.info("Number of ready PODs: {}", deployment.getStatus().getReadyReplicas());
    assertEquals(1, deployment.getStatus().getReadyReplicas());
    log.info("Scale up successful");
  }

  @SneakyThrows
  @Test
  @DisplayName("TC05 - Test scaling Connected Systems")
  void testScaleConnectedSystemsDownAndUp() {
    // Scale down Context Simulator
    scaleDeployment(CS_DEPLOYMENT_NAME, 0, 120);

    // Need to wait until terminating pods disappear
    waitForPodState(180, 0, CS_POD_NAME);
    log.info("Scale down successful");

    // Scale up Context Simulator
    scaleDeployment(CS_DEPLOYMENT_NAME, 1, 120);

    V1Deployment deployment =
        appApi.readNamespacedDeployment(CS_DEPLOYMENT_NAME, namespace, null, null, null);
    log.info("Number of ready PODs: {}", deployment.getStatus().getReadyReplicas());

    assertEquals(1, deployment.getStatus().getReadyReplicas());
    log.info("Scale up successful");
  }

  // NOTE: this can be used for CPU and memory usage while pod is under load
  @SneakyThrows
  @Test
  @DisplayName("TC03 - Read CPU and Memory metrics from all pods")
  void testGetSamplePodMetrics() {
    ApiClient client = Config.defaultClient();
    Configuration.setDefaultApiClient(client);

    Metrics metrics = new Metrics(client);
    NodeMetricsList list = metrics.getNodeMetrics();
    for (NodeMetrics item : list.getItems()) {
      System.out.println(item.getMetadata().getName());
      System.out.println("------------------------------");
      for (String key : item.getUsage().keySet()) {
        System.out.println("\t" + key);
        System.out.println("\t" + item.getUsage().get(key));
      }
      System.out.println();
    }

    for (PodMetrics item : metrics.getPodMetrics(namespace).getItems()) {
      System.out.println(item.getMetadata().getName());
      System.out.println("------------------------------");
      if (item.getContainers() == null) {
        continue;
      }
      for (ContainerMetrics container : item.getContainers()) {
        System.out.println(container.getName());
        System.out.println("-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
        for (String key : container.getUsage().keySet()) {
          System.out.println("\t" + key);
          System.out.println("\t" + container.getUsage().get(key));
        }
        System.out.println();
      }
    }
  }

  // NOTE: kept for reference, can be removed/refactored. This verifies if all expected pods are
  // included on the cluster
  @Test
  @DisplayName("TC01 - Verify Deployment is complete")
  void verifyDeploymentIsComplete() {
    V1PodList podList = getPodListFromCluster();
    List<String> podNameList = getPodNameList(getPodListFromCluster());

    String emsncPodName = getCurrentPodName(podList, EMSNC_POD_NAME);
    String connectedSystemsPodName = getCurrentPodName(podList, CS_POD_NAME);
    String contextSimName = getCurrentPodName(podList, CONTEXT_SIM_POD_NAME);

    assertNotNull(emsncPodName);
    assertNotNull(connectedSystemsPodName);
    log.info(emsncPodName);
    log.info(connectedSystemsPodName);

    assertThat(
        podNameList,
        containsInAnyOrder(
            "eric-data-coordinator-zk-0",
            "eric-oss-common-postgres-0",
            "eric-data-message-bus-kf-0",
            "eric-data-message-bus-kf-1",
            "eric-data-message-bus-kf-2",
            contextSimName,
            connectedSystemsPodName,
            emsncPodName));
  }

  @Test
  @DisplayName("TC04 - Test interaction with Simulator")
  void testInteractingWithSimulator() {
    K8sExecUtil.initClient();
    String simulatorCurrentPodName =
        getCurrentPodName(getPodListFromCluster(), CONTEXT_SIM_POD_NAME);
    String result = K8sExecUtil.executeK8sCommand(new String[] {"ls"}, simulatorCurrentPodName);
    assertNotNull(result);

    SimulatorProxyFactory simulatorProxyFactory =
        new SimulatorProxyFactory(simulatorCurrentPodName);

    SimulatedEnmRemote simulatedEnm = simulatorProxyFactory.getSimulatedEnm(1);
    simulatedEnm.setEventsPerCycle(0);
    assertEquals(0, simulatedEnm.getEventsPerCycle());
    simulatedEnm.setEventsPerCycle(1);
    assertEquals(1, simulatedEnm.getEventsPerCycle());
    simulatedEnm.reset(5);
  }

  @Test
  @DisplayName("TC07 - Test event flow with Simulator")
  void happyPathWithSimulatedEnmTest() {
    setupSimulator(2);

    await()
        .atMost(300, TimeUnit.SECONDS)
        .pollInterval(10, TimeUnit.SECONDS)
        .until(() -> checkRequiredMessagesReached(200, dmaap));

    log.info("remaining events at end of test enm1: " + enm1.getRemainingEventCycles());
    log.info("remaining events at end of test enm2: " + enm2.getRemainingEventCycles());

    log.info("msg count: " + dmaap.getMessageCount());
  }

  @Test
  @DisplayName("TC08 - Test EMSNC recovery when Quartz DB fails")
  void testEmsncRecoveryWhenQuartzDbFails() {
    setupSimulator(1);

    await()
        .atMost(300, TimeUnit.SECONDS)
        .pollInterval(10, TimeUnit.SECONDS)
        .until(() -> checkRequiredMessagesReached(100, dmaap));

    scaleStateFulSet(DATABASE_STATEFUL_SET_NAME, 0, 120);
    waitForPodState(180, 0, DATABASE_POD_NAME);
    log.info("Scale down of {} successful.", DATABASE_STATEFUL_SET_NAME);

    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    // check that the event count stays the same, so none are propagated
    await()
        .during(30, TimeUnit.SECONDS)
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> dmaap.getMessageCount() == 100);

    scaleStateFulSet(DATABASE_STATEFUL_SET_NAME, 1, 120);
    waitForPodState(180, 1, DATABASE_POD_NAME);
    log.info("Scale up of {} successful.", DATABASE_STATEFUL_SET_NAME);

    await()
        .atMost(300, TimeUnit.SECONDS)
        .pollInterval(10, TimeUnit.SECONDS)
        .until(() -> checkRequiredMessagesReached(200, dmaap));
  }

  @Test
  @DisplayName("TC09 - Test event flow while Subsystem Management is down")
  void testEventPropagationContinuesWithSubsystemManagementDown() {
    setupSimulator(1);

    await()
        .atMost(300, TimeUnit.SECONDS)
        .pollInterval(10, TimeUnit.SECONDS)
        .until(() -> checkRequiredMessagesReached(100, dmaap));

    scaleDeployment(SUBSYSTEM_MANAGEMENT_DEPLOYMENT_NAME, 0, 180);
    waitForPodState(180, 0, SUBSYSTEM_MANAGEMENT_POD_NAME);
    log.info("Scale down of {} successful", SUBSYSTEM_MANAGEMENT_POD_NAME);

    enm1.addEventCycles(1);
    enm2.addEventCycles(1);

    await()
        .atMost(300, TimeUnit.SECONDS)
        .pollInterval(10, TimeUnit.SECONDS)
        .until(() -> checkRequiredMessagesReached(200, dmaap));

    scaleDeployment(SUBSYSTEM_MANAGEMENT_DEPLOYMENT_NAME, 1, 180);
    waitForPodState(180, 1, SUBSYSTEM_MANAGEMENT_POD_NAME);
    log.info("Scale up of {} successful", SUBSYSTEM_MANAGEMENT_POD_NAME);
  }

  private void setupSimulator(int numberOfEventCycles) {
    V1PodList podList = getPodListFromCluster();
    String simulatorPodName = getCurrentPodName(podList, CONTEXT_SIM_POD_NAME);

    SimulatorProxyFactory simulatorProxyFactory = new SimulatorProxyFactory(simulatorPodName);

    dmaap = simulatorProxyFactory.getDmaapKafkaClient();
    dmaap.reset();

    assertEquals(0, dmaap.getMessageCount());

    enm1 = simulatorProxyFactory.getSimulatedEnm(1);
    enm2 = simulatorProxyFactory.getSimulatedEnm(2);

    enm1.reset(5);
    enm2.reset(5);
    enm1.addEventCycles(numberOfEventCycles);
    enm2.addEventCycles(numberOfEventCycles);
  }
}
