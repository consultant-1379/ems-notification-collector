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
package com.ericsson.oss.adc.emsnc.k8s.util;

import static org.awaitility.Awaitility.await;

import com.ericsson.oss.adc.emsnc.it.kafka.DmaapKafkaRemote;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;

@Slf4j
public class K8sTestBase {

  private static CoreV1Api api;
  protected static AppsV1Api appApi;
  protected static String namespace;

  protected static final String CS_DEPLOYMENT_NAME = "eric-eo-subsystem-management";
  protected static final String CS_POD_NAME = "eric-eo-subsystem-management";
  protected static final String CONTEXT_SIM_DEPLOYMENT_NAME = "context-simulator";
  protected static final String CONTEXT_SIM_POD_NAME = "context-simulator";
  protected static final String EMSNC_POD_NAME = "eric-oss-adc-ems-notification-collector";
  protected static final String EMSNC_DEPLOYMENT_NAME = "eric-oss-adc-ems-notification-collector";
  protected static final String SUBSYSTEM_MANAGEMENT_DEPLOYMENT_NAME =
      "eric-eo-subsystem-management";
  protected static final String SUBSYSTEM_MANAGEMENT_POD_NAME = "eric-eo-subsystem-management";

  protected static final String KAFKA_STATEFUL_SET_NAME = "eric-data-message-bus-kf";
  protected static final String KAFKA_POD_NAME = "eric-data-message-bus-kf";
  protected static final String DATABASE_STATEFUL_SET_NAME = "eric-oss-common-postgres";
  protected static final String DATABASE_POD_NAME = "eric-oss-common-postgres";

  private static final String K8S_DEFAULT_NAMESPACE = "k8s-bora-test";

  @SneakyThrows
  @BeforeAll
  static void initClient() {
    String k8sNsFromEnv = System.getenv("K8_NAMESPACE");

    if (k8sNsFromEnv != null) {
      namespace = k8sNsFromEnv;
      log.info("k8s namespace is: " + k8sNsFromEnv);
    } else {
      // Local execution. Namespace is set to username to avoid conflict on common cluster
      namespace =
          Optional.ofNullable(System.getProperty("user.name")).orElse(K8S_DEFAULT_NAMESPACE);
      log.info("Local execution detected, namespace is: {}", namespace);
    }

    String kubeConfigPath = System.getenv("KUBECONFIG");
    if (kubeConfigPath == null) {
      kubeConfigPath =
          System.getProperty("user.home") + File.separator + ".kube" + File.separator + "config";
    }

    ApiClient client =
        ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
    Configuration.setDefaultApiClient(client);
    api = new CoreV1Api();
    appApi = new AppsV1Api();
  }

  protected void waitForPodState(int timeoutSeconds, int expectedPodCount, String podName) {
    await()
        .atMost(timeoutSeconds, TimeUnit.SECONDS)
        .pollInterval(3, TimeUnit.SECONDS)
        .until(
            () -> {
              V1PodList podList = getPodListFromCluster();

              long countOfPods =
                  podList.getItems().stream()
                      .map(item -> Objects.requireNonNull(item.getMetadata()).getName())
                      .filter(name -> name.contains(podName))
                      .count();

              return countOfPods == expectedPodCount;
            });
  }

  @SneakyThrows
  protected V1PodList getPodListFromCluster() {
    return api.listNamespacedPod(
        namespace, null, null, null, null, null, null, null, null, null, null);
  }

  // Note: only returns first hit
  protected String getCurrentPodName(V1PodList podList, String podName) {
    for (V1Pod item : podList.getItems()) {
      String name = Objects.requireNonNull(item.getMetadata()).getName();
      if (name != null && name.contains(podName)) {
        return name;
      }
    }

    return null;
  }

  protected List<String> getPodNameList(V1PodList podList) {
    return podList.getItems().stream()
        .map(item -> Objects.requireNonNull(item.getMetadata()).getName())
        .collect(Collectors.toList());
  }

  @SneakyThrows
  protected V1Pod getPod(String podName) {
    V1PodList podList = getPodListFromCluster();
    String actualPodName = getCurrentPodName(podList, podName);
    return api.readNamespacedPod(actualPodName, namespace, null, null, null);
  }

  @SneakyThrows
  protected void scaleDeployment(String deploymentName, int replicaCount, int timeOutInSec) {
    V1Deployment deployment =
        appApi.readNamespacedDeployment(deploymentName, namespace, null, null, null);
    log.info(
        "Availabile replicas for deployment {} before scale: {}",
        deploymentName,
        deployment.getStatus().getAvailableReplicas());

    deployment.getSpec().setReplicas(replicaCount);
    appApi.replaceNamespacedDeployment(deploymentName, namespace, deployment, null, null, null);

    waitForDeploymentAvailable(deploymentName, replicaCount, timeOutInSec);
  }

  @SneakyThrows
  protected void scaleStateFulSet(String statefulSetName, int replicaCount, int timeoutInSec) {
    V1StatefulSet statefulSet =
        appApi.readNamespacedStatefulSet(statefulSetName, namespace, null, null, null);
    statefulSet.getSpec().setReplicas(replicaCount);
    appApi.replaceNamespacedStatefulSet(statefulSetName, namespace, statefulSet, null, null, null);
    waitForStatefulSetReady(statefulSetName, replicaCount, timeoutInSec);
  }

  private void waitForDeploymentAvailable(
      String deploymentName, int replicaCount, int timeoutInSec) {
    await()
        .atMost(timeoutInSec, TimeUnit.SECONDS)
        .pollInterval(3, TimeUnit.SECONDS)
        .until(
            () -> {
              V1Deployment pollDeployment =
                  appApi.readNamespacedDeployment(deploymentName, namespace, null, null, null);

              if (replicaCount == 0) {
                return pollDeployment.getStatus().getAvailableReplicas() == null;
              } else {
                return pollDeployment.getStatus().getAvailableReplicas() != null
                    && pollDeployment.getStatus().getAvailableReplicas() == replicaCount;
              }
            });
  }

  private void waitForStatefulSetReady(String statefulSetName, int replicaCount, int timeoutInSec) {
    await()
        .atMost(timeoutInSec, TimeUnit.SECONDS)
        .pollInterval(3, TimeUnit.SECONDS)
        .until(
            () -> {
              V1StatefulSet pollStatefulSet =
                  appApi.readNamespacedStatefulSet(statefulSetName, namespace, null, null, null);

              if (replicaCount == 0) {
                return pollStatefulSet.getStatus().getReadyReplicas() == null;
              } else {
                return pollStatefulSet.getStatus().getReadyReplicas() != null
                    && pollStatefulSet.getStatus().getReadyReplicas() == replicaCount;
              }
            });
  }

  protected Boolean checkRequiredMessagesReached(int requiredMessages, DmaapKafkaRemote dmaap) {
    long messageCount = dmaap.getMessageCount();
    log.info("DMaaP Kafka currently has {} events", messageCount);
    // TODO should be ==requiredMessages after resolving kafka idempotency issues
    return messageCount >= requiredMessages;
  }
}
