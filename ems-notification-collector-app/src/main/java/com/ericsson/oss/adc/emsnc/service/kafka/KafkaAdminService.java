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
package com.ericsson.oss.adc.emsnc.service.kafka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@SuppressWarnings("java:S2142")
public class KafkaAdminService {

  @Value("${emsnc.kafka.emsnc-internal.bootstrap-servers}")
  private String emsncBootstrapAddress;

  @Value("${emsnc.kafka.emsnc-internal.topic-count}")
  private int emsncInternalTopicCount;

  @Value("${emsnc.kafka.dmaap.bootstrap-servers}")
  private String dmaapBootstrapAddress;

  @Value("${emsnc.kafka.dmaap.propagation-topic-name}")
  private String dmaapTopicName;

  @Value("${emsnc.kafka.emsnc-internal.partition-count}")
  private int numberOfInternalTopicPartitions;

  @Value("${emsnc.kafka.emsnc-internal.replication-factor}")
  private short internalTopicReplicationFactor;

  @Value("${emsnc.kafka.dmaap.partition-count}")
  private int numberOfDmaapPartitions;

  @Value("${emsnc.kafka.dmaap.replication-factor}")
  private short dmaapTopicReplicationFactor;

  private final List<String> emsncKafkaTopicNames;

  public KafkaAdminService(@Value("${emsnc.kafka.emsnc-internal.topic-count}") int topicCount) {
    emsncKafkaTopicNames =
        IntStream.range(0, topicCount)
            .mapToObj(KafkaTopicHashService::generateTopicName)
            .collect(Collectors.toList());
  }

  public void createEmsncKafkaTopics() {
    List<NewTopic> topics = new ArrayList<>();
    for (int i = 0; i < emsncInternalTopicCount; ++i) {
      NewTopic newTopic =
          new NewTopic(
              emsncKafkaTopicNames.get(i),
              numberOfInternalTopicPartitions,
              internalTopicReplicationFactor);
      topics.add(newTopic);
    }

    try (final AdminClient adminClient = getKafkaAdminClient(emsncBootstrapAddress)) {
      adminClient.createTopics(topics);
      log.info(
          "EMSNC Topics created: {}",
          topics.stream().map(NewTopic::name).collect(Collectors.toList()));
    } catch (Exception e) {
      log.error("Failed to create EMSNC topics:", e);
      throw e;
    }
  }

  public void createDmaapKafkaTopics() {
    NewTopic receiverNewTopic =
        new NewTopic(dmaapTopicName, numberOfDmaapPartitions, dmaapTopicReplicationFactor);

    try (final AdminClient adminClient = getKafkaAdminClient(dmaapBootstrapAddress)) {
      adminClient.createTopics(Collections.singleton(receiverNewTopic));
      log.info("DMaaP Topic created: {}", dmaapTopicName);
    } catch (Exception e) {
      log.error("Failed to create DMaaP topic {}:", dmaapTopicName, e);
      throw e;
    }
  }

  protected AdminClient getKafkaAdminClient(String bootstrapAddress) {
    Map<String, Object> props = new HashMap<>();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 8000);
    props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 8000);
    return KafkaAdminClient.create(props);
  }

  private boolean isKafkaAvailable(String bootstrapAddress, List<String> expectedTopicNames) {
    try (final AdminClient adminClient = getKafkaAdminClient(bootstrapAddress)) {
      ListTopicsResult topics = adminClient.listTopics();
      Set<String> topicNames;
      try {
        topicNames = topics.names().get(8L, TimeUnit.SECONDS);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        log.error("Getting topics from Kafka broker {} failed.", bootstrapAddress, e);
        return false;
      }

      List<String> missingTopics =
          expectedTopicNames.stream()
              .filter(expectedTopic -> !topicNames.contains(expectedTopic))
              .collect(Collectors.toList());
      if (!missingTopics.isEmpty()) {
        log.error("Topics missing from Kafka broker {}: {}", bootstrapAddress, missingTopics);
        return false;
      }
      return true;
    }
  }

  public boolean isEmsncKafkaAvailable() {
    return isKafkaAvailable(emsncBootstrapAddress, emsncKafkaTopicNames);
  }

  public boolean isDmaapKafkaAvailable() {
    return isKafkaAvailable(dmaapBootstrapAddress, Collections.singletonList(dmaapTopicName));
  }
}
