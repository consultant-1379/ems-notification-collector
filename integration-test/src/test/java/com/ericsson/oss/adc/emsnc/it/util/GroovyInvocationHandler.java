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

import com.ericsson.oss.adc.emsnc.it.AllowedTypes;
import com.ericsson.oss.adc.emsnc.k8s.util.K8sExecUtil;
import com.google.gson.Gson;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public class GroovyInvocationHandler implements InvocationHandler {
  private final String farObject;
  private final GenericContainer simulatorContainer;
  private final String podName;

  public GroovyInvocationHandler(
      String farObject, GenericContainer simulatorContainer, String podName) {
    this.farObject = farObject;
    this.simulatorContainer = simulatorContainer;
    this.podName = podName;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    checkUnsupportedArguments(args);
    String groovyCmd = buildGroovyCommand(method, args);

    log.info("Invoked method: {}, translated to groovy as {}", method.getName(), groovyCmd);
    String base64EncodedCommand =
        Base64.getEncoder().encodeToString(groovyCmd.getBytes(StandardCharsets.UTF_8));
    String[] curlCommand = new String[] {"curl", "localhost:1234/exec/" + base64EncodedCommand};

    String result = executeGroovyCommand(curlCommand);
    log.info("Simulator result was '{}'", result);
    if (result.startsWith("error:")) {
      throw new RuntimeException("Error in processing: " + result);
    } else if (result.equals("void")) {
      return null;
    } else {
      return new Gson().fromJson(result, method.getReturnType());
    }
  }

  private String executeGroovyCommand(String[] tmuxCmd) throws InterruptedException {
    if (podName != null && simulatorContainer == null) {
      K8sExecUtil.initClient();
      return K8sExecUtil.executeK8sCommand(tmuxCmd, podName);
    } else if (simulatorContainer != null && podName == null) {
      return DockerExecUtil.executeDockerCommand(tmuxCmd, simulatorContainer);
    } else if (simulatorContainer != null) {
      throw new IllegalArgumentException(
          "Bad arguments. You can only use Pod or SimulatorContainer at the same time.");
    } else {
      throw new IllegalArgumentException("Bad arguments. No container or pod name provided.");
    }
  }

  private void checkUnsupportedArguments(Object[] args) {
    Arrays.stream(Optional.ofNullable(args).orElse(new Object[0]))
        .filter(a -> !AllowedTypes.isAllowed(a.getClass()))
        .forEach(
            a -> {
              throw new UnsupportedOperationException(
                  "Argument of type '" + a.getClass() + "' not supported");
            });
  }

  private String buildGroovyCommand(Method method, Object[] args) {
    return farObject
        + "."
        + method.getName()
        + "("
        + Arrays.stream(Optional.ofNullable(args).orElse(new Object[0]))
            .map(a -> convertToString(a))
            .collect(Collectors.joining(","))
        + ")";
  }

  private String convertToString(Object a) {
    if (a.getClass() == String.class) {
      return "\"" + a + "\"";
    } else {
      return String.valueOf(a);
    }
  }
}
