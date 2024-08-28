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
import com.ericsson.oss.adc.emsnc.it.kafka.DmaapKafkaRemote;
import com.ericsson.oss.adc.emsnc.wiremock.SimulatedConnectedSystemsRemote;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.model.Network.Ipam;
import com.github.dockerjava.api.model.Network.Ipam.Config;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

@Slf4j
public class IntegrationTestBase {

  protected static final String TEST_EMSNC_KAFKA_NAME = "test-emsnc-kafka";
  protected static final String TEST_DMAAP_KAFKA_NAME = "test-dmaap-kafka";
  protected static final String TEST_POSTGRES_NAME = "test-postgres";
  protected static final String TEST_EMSNC_NAME = "test-emsnc";
  protected static final String TEST_ZK_NAME = "test-zk";
  protected static final String TEST_SIMULATOR_NAME = "test-simulator";
  protected static final String TEST_DMAAP_KAFKA_PORT = "19093";
  protected static final String TEST_EMSNC_KAFKA_PORT = "19092";
  protected static final String TEST_RESULT_TOPIC = "result-topic";

  protected static final int DEFAULT_ENM_EVENTS_PER_CYCLE = 5;

  private static final Network testDockerNetwork = Network.newNetwork();
  private static final String DOCKER_NETWORK_CIDR = "172.44.0.0/16";
  private static final String DOCKER_NETWORK_GW = "172.44.0.1";

  private static final String ISOLATION = "IDEMPOTENT";

  protected static GenericContainer postgresContainer;
  protected static GenericContainer contextSimulatorContainer;
  protected static GenericContainer emsncZooKeeperContainer;
  protected static GenericContainer emsncKafkaContainer;
  protected static GenericContainer dmaapKafkaContainer;
  protected static GenericContainer dmaapZookeeperContainer;
  protected static List<GenericContainer> emsncContainers = new ArrayList();
  private static int containerIndex = 0;

  protected SimulatorProxyFactory simulatorProxyFactory =
      new SimulatorProxyFactory(contextSimulatorContainer);
  protected DmaapKafkaRemote dmaap = simulatorProxyFactory.getDmaapKafkaClient();
  protected SimulatedConnectedSystemsRemote cs =
      simulatorProxyFactory.getSimulatedConnectedSystems();
  protected SimulatedEnmRemote enm1 = simulatorProxyFactory.getSimulatedEnm(1);
  protected SimulatedEnmRemote enm2 = simulatorProxyFactory.getSimulatedEnm(2);

  private long startTimestamp;

  @BeforeAll
  public static void setupSimulator() {
    assureContextSimulatorRunning();
  }

  @SneakyThrows
  @BeforeEach
  public void clearExistingLogs() {
    DockerExecUtil.executeDockerCommand(
        new String[] {"truncate", "/tmp/simulator.log", "--size", "0"}, contextSimulatorContainer);
    // needed to be able to save kafkacat logs from the correct timestamp
    startTimestamp = System.currentTimeMillis();
  }

  @AfterEach
  @SneakyThrows
  public void saveTestLogs(TestInfo testInfo) {
    new File("./target/it-logs/").mkdirs();
    contextSimulatorContainer.copyFileFromContainer(
        "/tmp/simulator.log",
        "./target/it-logs/"
            + testInfo.getTestClass().get().getSimpleName()
            + "_"
            + testInfo.getTestMethod().get().getName()
            + ".log");
    try (FileWriter writer =
        new FileWriter(
            "./target/it-logs/"
                + testInfo.getTestClass().get().getSimpleName()
                + "_"
                + testInfo.getTestMethod().get().getName()
                + ".kafka.log")) {

      String[] command = {
        "kafkacat",
        "-C",
        "-b",
        "test-dmaap-kafka:19093",
        "-t",
        "result-topic",
        "-o",
        "s@" + startTimestamp,
        "-e"
      };
      writer.append(DockerExecUtil.executeDockerCommand(command, contextSimulatorContainer));
    }
  }

  public void setUpStandardEnvironment() {
    assurePostgresState(true);
    assureEmsncKafkaState(true);
    assureDmaapKafkaState(true);
    emsNcInstanceTarget(1);

    enm1.reset(DEFAULT_ENM_EVENTS_PER_CYCLE);
    enm2.reset(DEFAULT_ENM_EVENTS_PER_CYCLE);
    cs.reset();
    dmaap.reset();
  }

  protected static void emsNcInstanceTarget(int count) {
    // only keep healthy instances
    emsncContainers.removeIf(
        c -> {
          if (c.isRunning() && !c.isHealthy()) {
            c.stop();
            await().atMost(30, TimeUnit.SECONDS).until(() -> !c.isRunning());
          }
          return !c.isRunning();
        });

    if (count > emsncContainers.size()) {
      int requiredNewEmsncContainers = count - emsncContainers.size();
      for (int i = 0; i < requiredNewEmsncContainers; i++) {
        startEmsncExpectingHealthy();
      }
    } else {
      int removeCount = emsncContainers.size() - count;
      for (int i = 0; i < removeCount; i++) {
        emsncContainers.get(0).stop();
        await().atMost(30, TimeUnit.SECONDS).until(() -> !emsncContainers.get(0).isRunning());
        emsncContainers.remove(0);
      }
    }
  }

  protected void startEmsncExpectingToFail(LogConsumer logVerifier) {
    GenericContainer emsncContainer = createNewEmsncContainer();

    log.info("Starting ems-n-c");
    emsncContainer.start();

    emsncContainer.followOutput(logVerifier);

    await()
        .atMost(300, TimeUnit.SECONDS)
        .until(() -> logVerifier.verifyMessageFromOutputAppeared());

    log.info("EMSNC failed to start");

    // TODO could even terminate, could be !isRunning
    await().atMost(300, TimeUnit.SECONDS).until(() -> !emsncContainer.isHealthy());

    log.info("EMSNC container stopped");
    emsncContainer.stop();
  }

  private static void startEmsncExpectingHealthy() {
    GenericContainer emsncContainer = createNewEmsncContainer();

    log.info("Starting ems-n-c");
    emsncContainer.start();
    emsncContainer.followOutput(new LogConsumer("emsnc" + containerIndex++));

    await().atMost(300, TimeUnit.SECONDS).until(emsncContainer::isHealthy);
  }

  protected static GenericContainer createNewEmsncContainer() {
    GenericContainer emsncContainer =
        new GenericContainer("emsnc-test-image")
            .withNetwork(testDockerNetwork)
            .withNetworkAliases(TEST_EMSNC_NAME)
            .withEnv("EMSNC_KAFKA_HOST", TEST_EMSNC_KAFKA_NAME)
            .withEnv("EMSNC_KAFKA_PORT", TEST_EMSNC_KAFKA_PORT)
            .withEnv("EMSNC_KAFKA_PRODUCER_ISOLATION", ISOLATION)
            .withEnv("DMAAP_KAFKA_HOST", TEST_DMAAP_KAFKA_NAME)
            .withEnv("DMAAP_KAFKA_PORT", TEST_DMAAP_KAFKA_PORT)
            .withEnv("DMAAP_KAFKA_TOPIC", TEST_RESULT_TOPIC)
            .withEnv("DMAAP_KAFKA_PARTITION_COUNT", "1")
            .withEnv("DMAAP_KAFKA_PRODUCER_ISOLATION", ISOLATION)
            .withEnv("SUBSCRIPTION_NE_TYPES", "RadioNode,Router6675")
            .withEnv("DB_HOST", TEST_POSTGRES_NAME)
            .withEnv("DB_PORT", "5432")
            .withEnv("DB_NAME", "emsnc_db")
            .withEnv("DB_USERNAME", "quartz-node")
            .withEnv("DB_PASSWORD", "quartz-pg-pass")
            .withEnv("EMSNC_LOG_LEVEL", "DEBUG")
            .withEnv("CONNECTED_SYSTEMS_HOST", TEST_SIMULATOR_NAME)
            .withEnv("CONNECTED_SYSTEMS_PORT", "8280")
            .withEnv("CONNECTED_SYSTEMS_POLLING_FREQUENCY", "30_SEC")
            .withEnv("ENM_POLLING_FREQUENCY", "10_SEC");
    emsncContainers.add(emsncContainer);
    return emsncContainer;
  }

  private static void assureEmsncZookeeperRunning() {
    emsncZooKeeperContainer =
        Optional.ofNullable(emsncZooKeeperContainer)
            .orElseGet(
                () ->
                    new GenericContainer("bitnami/zookeeper:latest")
                        .withNetwork(testDockerNetwork)
                        .withNetworkAliases(TEST_ZK_NAME)
                        .withEnv("ALLOW_ANONYMOUS_LOGIN", "yes"));
    if (!emsncZooKeeperContainer.isRunning()) {
      log.info("Starting EMSNC ZooKeeper");
      emsncZooKeeperContainer.start();
    }
  }

  protected static void assurePostgresState(boolean shouldRun) {
    postgresContainer =
        Optional.ofNullable(postgresContainer)
            .orElseGet(
                () ->
                    new GenericContainer("postgres:10.16")
                        .withNetwork(testDockerNetwork)
                        .withNetworkAliases(TEST_POSTGRES_NAME)
                        .withEnv("POSTGRES_USER", "quartz-node")
                        .withEnv("POSTGRES_DB", "emsnc_db")
                        .withEnv("POSTGRES_PASSWORD", "quartz-pg-pass"));
    if (!postgresContainer.isRunning() && shouldRun) {
      log.info("Starting PostgreSQL");
      postgresContainer.start();
    } else if (postgresContainer.isRunning() && !shouldRun) {
      log.info("Stopping PostgreSQL");
      postgresContainer.stop();
    }
  }

  protected static void assureContextSimulatorRunning() {

    contextSimulatorContainer =
        Optional.ofNullable(contextSimulatorContainer)
            .orElseGet(
                () ->
                    new GenericContainer("emsnc-context-simulator:latest")
                        .withNetwork(testDockerNetwork)
                        .withNetworkAliases(TEST_SIMULATOR_NAME)
                        .withEnv(
                            "KAFKA_BOOTSTRAP_SERVERS",
                            TEST_DMAAP_KAFKA_NAME + ":" + TEST_DMAAP_KAFKA_PORT)
                        .withEnv("KAFKA_TOPIC", TEST_RESULT_TOPIC)
                        .withEnv("EVENT_CYCLES", "0")
                        .withEnv("ENM_HOST", TEST_SIMULATOR_NAME));
    if (!contextSimulatorContainer.isRunning()) {
      log.info("Starting simulator");
      contextSimulatorContainer.start();
      await().atMost(60, TimeUnit.SECONDS).until(contextSimulatorContainer::isHealthy);
    }
  }

  protected Boolean checkRequiredMessagesReached(int requiredMessages) {
    long messageCount = dmaap.getMessageCount();
    log.info("DMaaP Kafka currently has {} events", messageCount);
    return messageCount == requiredMessages;
  }

  private static void assureDmaapZookeeperRunning() {
    dmaapZookeeperContainer =
        Optional.ofNullable(dmaapZookeeperContainer)
            .orElseGet(
                () ->
                    new GenericContainer("bitnami/zookeeper:latest")
                        .withNetwork(testDockerNetwork)
                        .withNetworkAliases("test-dmaap-zk")
                        .withEnv("ALLOW_ANONYMOUS_LOGIN", "yes"));
    if (!dmaapZookeeperContainer.isRunning()) {
      log.info("Starting DMaaP ZooKeeper");
      dmaapZookeeperContainer.start();
    }
  }

  protected static void assureDmaapKafkaState(boolean shouldRun) {
    dmaapKafkaContainer =
        Optional.ofNullable(dmaapKafkaContainer)
            .orElseGet(
                () ->
                    new GenericContainer("bitnami/kafka:latest")
                        .withNetwork(testDockerNetwork)
                        .withNetworkAliases(TEST_DMAAP_KAFKA_NAME)
                        .withEnv("KAFKA_CFG_ZOOKEEPER_CONNECT", "test-dmaap-zk" + ":2181")
                        .withEnv("ALLOW_PLAINTEXT_LISTENER", "yes")
                        .withEnv(
                            "KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP", "INTERNAL-DMAAP:PLAINTEXT")
                        .withEnv(
                            "KAFKA_CFG_LISTENERS",
                            "INTERNAL-DMAAP://0.0.0.0:" + TEST_DMAAP_KAFKA_PORT)
                        .withEnv(
                            "KAFKA_CFG_ADVERTISED_LISTENERS",
                            "INTERNAL-DMAAP://"
                                + TEST_DMAAP_KAFKA_NAME
                                + ":"
                                + TEST_DMAAP_KAFKA_PORT)
                        .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "INTERNAL-DMAAP"));

    if (!dmaapKafkaContainer.isRunning() && shouldRun) {
      assureDmaapZookeeperRunning();
      log.info("Starting DMaaP Kafka");
      dmaapKafkaContainer.start();
      await().atMost(30, TimeUnit.SECONDS).until(() -> dmaapKafkaContainer.isRunning());
    } else if (dmaapKafkaContainer.isRunning() && !shouldRun) {
      log.info("Stopping DMaaP Kafka");
      dmaapKafkaContainer.stop();
      dmaapZookeeperContainer.stop();
    } else {
      log.info("Leaving DMaaP Kafka as it is");
    }
  }

  protected static void assureEmsncKafkaState(boolean shouldRun) {
    emsncKafkaContainer =
        Optional.ofNullable(emsncKafkaContainer)
            .orElseGet(
                () ->
                    new GenericContainer("bitnami/kafka:latest")
                        .withNetwork(testDockerNetwork)
                        .withNetworkAliases(TEST_EMSNC_KAFKA_NAME)
                        .withEnv("KAFKA_CFG_ZOOKEEPER_CONNECT", TEST_ZK_NAME + ":2181")
                        .withEnv("ALLOW_PLAINTEXT_LISTENER", "yes")
                        .withEnv(
                            "KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP", "INTERNAL-EMSNC:PLAINTEXT")
                        .withEnv(
                            "KAFKA_CFG_LISTENERS",
                            "INTERNAL-EMSNC://0.0.0.0:" + TEST_EMSNC_KAFKA_PORT)
                        .withEnv(
                            "KAFKA_CFG_ADVERTISED_LISTENERS",
                            "INTERNAL-EMSNC://"
                                + TEST_EMSNC_KAFKA_NAME
                                + ":"
                                + TEST_EMSNC_KAFKA_PORT)
                        .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "INTERNAL-EMSNC"));
    if (!emsncKafkaContainer.isRunning() && shouldRun) {
      log.info("Starting EMSNC Kafka");
      assureEmsncZookeeperRunning();
      emsncKafkaContainer.start();
      await().atMost(30, TimeUnit.SECONDS).until(() -> emsncKafkaContainer.isRunning());
    } else if (emsncKafkaContainer.isRunning() && !shouldRun) {
      log.info("Stopping EMSNC Kafka");
      emsncKafkaContainer.stop();
      emsncZooKeeperContainer.stop();
    } else {
      log.info("Leaving EMSNC Kafka as it is");
    }
  }

  protected void waitForExactMessageCount(int requiredMessages) {
    await()
        .atMost(150, TimeUnit.SECONDS)
        .until(
            () -> {
              long messageCount = dmaap.getMessageCount();
              log.info(
                  "DMaaP Kafka currently has {} events (waiting to reach {})",
                  messageCount,
                  requiredMessages);
              return messageCount >= requiredMessages;
            });
    await()
        .atMost(30, TimeUnit.SECONDS)
        .during(20, TimeUnit.SECONDS)
        .until(
            () -> {
              long messageCount = dmaap.getMessageCount();
              log.info(
                  "DMaaP Kafka currently has {} events (expecting to stay {})",
                  messageCount,
                  requiredMessages);
              return messageCount == requiredMessages;
            });
    assertEquals(0, dmaap.getFailedOrderCount());
  }

  // TODO: use this in case of local test execution for ELX
  private static Network buildNetworkWithSubnet() {
    Consumer<CreateNetworkCmd> cmdModifier =
        (createNetworkCmd) -> {
          Config ipamConfig =
              new Config().withSubnet(DOCKER_NETWORK_CIDR).withGateway(DOCKER_NETWORK_GW);
          createNetworkCmd.withIpam(new Ipam().withConfig(ipamConfig));
        };

    return Network.builder().driver("bridge").createNetworkCmdModifier(cmdModifier).build();
  }
}
