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

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import com.ericsson.oss.adc.emsnc.it.kafka.DmaapKafkaConsumer;
import com.ericsson.oss.adc.emsnc.wiremock.SimulatedConnectedSystems;
import com.google.gson.Gson;
import groovy.lang.GroovyShell;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import spark.Request;
import spark.Spark;

@Slf4j
@SuppressWarnings({
  "squid:S106",
  "java:S2142"
}) // intentionally using System.out and not interrupting the thread
public class StandaloneSimulator {
  public static final String RESULT_ERROR = "error";
  public static final String RESULT_VOID = "void";
  public static final String GROOVY_PROMPT = "groovy >\n";

  private SimulatedConnectedSystems cs;

  private String csHost = System.getProperty("csHost");
  private String csPort = Optional.ofNullable(System.getProperty("csPort")).orElse("8280");
  private String enmPorts = Optional.ofNullable(System.getProperty("enmPorts")).orElse("8281,8282");
  private String maxEventCycles =
      Optional.ofNullable(System.getProperty("maxEventCycles"))
          .orElse(String.valueOf(Integer.MAX_VALUE));
  private List<SimulatedEnm> enmList;
  private DmaapKafkaConsumer dmaap;
  private GroovyShell shell;
  private SubsystemManagement subsystemManagement;

  public static void main(String[] args) {
    new StandaloneSimulator().initializeInteractiveSimulation();
  }

  public StandaloneSimulator() {
    if (isUsingRealSubsystemManager()) {
      initializeSubsystemManager();
    }
    initializeSimulation();
  }

  private void initializeInteractiveSimulation() {
    BufferedReader consoleReader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    StringBuilder outputBuilder = new StringBuilder();

    outputBuilder.append("Running simulation in interactive mode").append("\n");
    outputBuilder.append("Close the Groovy Console window to stop...").append("\n");
    shell = new GroovyShell();
    if (!isUsingRealSubsystemManager()) {
      shell.setVariable("cs", cs);
      outputBuilder
          .append("Connected Systems: ")
          .append(cs.getWireMockServer().baseUrl())
          .append("\n");
    } else {
      outputBuilder
          .append("Real Connected Systems: ")
          .append(csHost)
          .append(":")
          .append(csPort)
          .append("\n");
    }

    if (dmaap != null) {
      shell.setVariable("dmaap", dmaap);
    }

    for (int i = 0; i < enmList.size(); ++i) {
      SimulatedEnm enm = enmList.get(i);
      outputBuilder
          .append("ENM ")
          .append(i)
          .append(": ")
          .append(enm.getUri())
          .append(" ")
          .append(enm.getUser())
          .append(" / ")
          .append(enm.getPassword())
          .append("\n");
      shell.setVariable("enm" + (i + 1), enm);
    }

    outputBuilder
        .append("Available variables: ")
        .append(shell.getContext().getVariables().keySet())
        .append("\n");
    outputBuilder
        .append("Start typing commands to interact with the groovy interpreter")
        .append("\n");

    Spark.port(1234);
    Spark.get("/exec/:command", (req, res) -> executeRestCommand(req));

    while (System.currentTimeMillis() < Long.MAX_VALUE) { // meaningful end condition
      handleGroovyPrompt(consoleReader, outputBuilder);
    }
  }

  @SneakyThrows
  private void handleGroovyPrompt(BufferedReader consoleReader, StringBuilder outputBuilder) {
    TimeUnit.MILLISECONDS.sleep(500); // logs may still be printed
    outputBuilder.append(GROOVY_PROMPT);
    System.out.print(outputBuilder);
    outputBuilder.setLength(0);
    String commandFromInput = consoleReader.readLine();

    try {
      Object result = shell.evaluate(commandFromInput);
      if (result != null && (AllowedTypes.isAllowed(result.getClass()))) {
        outputBuilder
            .append("\n[ Command '")
            .append(commandFromInput)
            .append("' successfully executed ]: '")
            .append(new Gson().toJson(result))
            .append("'\n");
      } else if (result != null) {
        outputBuilder
            .append("\n[ Command '")
            .append(commandFromInput)
            .append("' successfully executed ]: ")
            .append(RESULT_VOID)
            .append("\n");
        outputBuilder.append("Value as text: ").append(result).append("\n");
      } else {

        outputBuilder
            .append("\n[ Command '")
            .append(commandFromInput)
            .append("' successfully executed ]: ")
            .append(RESULT_VOID)
            .append("\n");
      }
    } catch (Exception e) {
      outputBuilder
          .append("\n[ Command '")
          .append(commandFromInput)
          .append("' failed ]: ")
          .append(RESULT_ERROR)
          .append("\n");
      log.error("Exception in Groovy execution", e);
    }
  }

  private String executeRestCommand(Request req) {
    String encodedCommand = req.params(":command");
    try {
      String command = new String(Base64.getDecoder().decode(encodedCommand), "utf-8");
      Object result = shell.evaluate(command);
      if (result == null) {
        return "void";
      } else {
        if (AllowedTypes.isAllowed(result.getClass())) {
          return new Gson().toJson(result);
        } else {
          return "void";
        }
      }
    } catch (Exception e) {
      log.error("Exception", e);
      StringWriter stringWriter = new StringWriter();
      PrintWriter writer = new PrintWriter(stringWriter);
      e.printStackTrace(writer);
      return "error:" + stringWriter;
    }
  }

  @SneakyThrows
  private void initializeSubsystemManager() {
    subsystemManagement = new SubsystemManagement(csHost, csPort);
    try {
      subsystemManagement.deleteExistingSubsystems(subsystemManagement.getExistingSubsystems());
    } catch (IOException e) {
      log.error(
          "Getting and deleting subsystems from {}:{} failed. Sleeping...", csHost, csPort, e);
      TimeUnit.SECONDS.sleep(10);
      log.info("Retrying getting and deleting subsystems.");
      initializeSubsystemManager();
    }
  }

  private void initializeSimulation() {
    Map<String, Integer> targets =
        Map.of(
            "RadioNode",
            SimulatedEnm.DEFAULT_TARGET_COUNT,
            "Router6675",
            SimulatedEnm.DEFAULT_TARGET_COUNT);
    enmList =
        Arrays.stream(enmPorts.split(","))
            .map(Integer::valueOf)
            .map(port -> initializeEnm(targets, port))
            .collect(Collectors.toList());

    if (!isUsingRealSubsystemManager()) {
      log.info("csHost not provided - starting SimulatedConnectedSystems");
      cs =
          SimulatedConnectedSystems.builder()
              .port(Integer.valueOf(csPort))
              .enmList(enmList)
              .build()
              .start();
    }

    if (DmaapKafkaConsumer.getKafkaConsumerBootstrapServers() != null) {
      dmaap = new DmaapKafkaConsumer();
      new Thread(() -> dmaap.runConsumer(), "DmaapKafkaConsumer").start();
    }
  }

  private SimulatedEnm initializeEnm(Map<String, Integer> targets, Integer port) {
    SimulatedEnm enm =
        SimulatedEnm.builder()
            .port(port)
            .name("enm-" + port)
            .targets(targets)
            .maxEventCycles(Integer.parseInt(maxEventCycles))
            .eventsPerCycle(3)
            .build()
            .start();

    if (isUsingRealSubsystemManager()) {
      registerEnmInSubsystemManager(enm);
    }
    return enm;
  }

  @SneakyThrows
  private void registerEnmInSubsystemManager(SimulatedEnm enm) {
    log.info("Registering {} in Subsystem Manager at {}:{}", enm.getName(), csHost, csPort);
    boolean registrationComplete = false;
    while (!registrationComplete) {
      try {
        subsystemManagement.registerEnmInSubsystemManager(
            enm, SimulatedEnm.DEFAULT_USER, SimulatedEnm.DEFAULT_PASSWORD);
        registrationComplete = true;
      } catch (IOException e) {
        log.error("Failed to register ENM {} in Subsystem Manager. Sleeping...", enm.getName(), e);
        TimeUnit.SECONDS.sleep(10);
        log.info("Retrying registration.");
      }
    }
  }

  private boolean isUsingRealSubsystemManager() {
    return csHost != null && !csHost.equalsIgnoreCase("localhost");
  }
}
