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

import io.kubernetes.client.Exec;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.Streams;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class K8sExecUtil {

  private static CoreV1Api api;
  private static AppsV1Api appApi;
  private static String namespace;
  private static final String K8S_DEFAULT_NAMESPACE = "k8s-bora-test";

  @SneakyThrows
  public static void initClient() {
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

  @SneakyThrows
  public static String executeK8sCommand(String[] args, String podName) {
    log.info("GroovyCmd to be executed on Pod {} : {}", podName, Arrays.toString(args));

    Exec exec = new Exec();
    final Process proc = exec.exec(namespace, podName, args, false, false);

    String cmdResponse = null;
    try {
      Reader targetReader = new InputStreamReader(proc.getInputStream());
      cmdResponse = Streams.toString(targetReader);
    } catch (Exception e) {
      e.printStackTrace();
    }

    proc.waitFor();
    log.info("pid exit code: {}", proc.exitValue());
    proc.destroy();

    log.info("GroovyCmd response: {}", cmdResponse);
    return cmdResponse;
  }
}
