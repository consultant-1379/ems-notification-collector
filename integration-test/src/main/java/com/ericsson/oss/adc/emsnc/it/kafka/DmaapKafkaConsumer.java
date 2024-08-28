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
package com.ericsson.oss.adc.emsnc.it.kafka;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

@Getter
@Slf4j
public class DmaapKafkaConsumer implements DmaapKafkaRemote {

  private String bootstrapServers = getKafkaConsumerBootstrapServers();

  Map<String, Instant> timeStampsByNetworkElement = new HashMap();

  public static String getKafkaConsumerBootstrapServers() {
    return System.getProperty("kafkaConsumerBootstrapServers");
  }

  private static String topic =
      Optional.ofNullable(System.getProperty("kafkaConsumerTopic")).orElse("dmaap-result-topic");

  private long messageCount = 0;
  private long failedOrderCount = 0;

  private Consumer<String, String> createConsumer() {
    final Properties props = new Properties();
    props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.setProperty(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.setProperty(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "IntegrationTestConsumer");
    props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    log.debug(props.toString());
    final Consumer<String, String> consumer = new KafkaConsumer<>(props);
    consumer.subscribe(Collections.singletonList(topic));
    return consumer;
  }

  @SneakyThrows
  public void runConsumer() {
    while (System.currentTimeMillis() < Long.MAX_VALUE) { // meaningful end condition
      try {
        try (Consumer<String, String> consumer = createConsumer()) {
          ConsumerRecords<String, String> consumerRecords = consumer.poll(Duration.ofMillis(10000));
          log.debug("Polling topic {}", topic);
          consumerRecords
              .records(topic)
              .forEach(
                  record -> {
                    try {
                      verifyConsumerRecord(record);
                      log.info("Received: {}", record);
                    } catch (Exception e) {
                      log.error("verifyConsumerRecord thrown exception", e);
                    }
                  });
          consumer.commitSync();
        }
      } catch (Exception e) {
        // ignore, retry
        log.error("Failed to poll Kafka topic {}", topic, e);
        TimeUnit.SECONDS.sleep(5);
      }
    }
  }

  private void verifyConsumerRecord(ConsumerRecord<String, String> consumerRecord) {
    messageCount++;
    String yangEventString = consumerRecord.value();

    JsonObject convertedObject = new Gson().fromJson(yangEventString, JsonObject.class);

    String timeStamp = getTimeStampForVerification(convertedObject);
    Instant actualTimeStamp = Instant.parse(timeStamp);
    String targetName = getTargetName(convertedObject);

    if (timeStampsByNetworkElement.containsKey(targetName)) {
      if (timeStampsByNetworkElement.get(targetName).compareTo(actualTimeStamp) < 1) {
        timeStampsByNetworkElement.put(targetName, actualTimeStamp);
      } else {
        failedOrderCount++;
      }
    } else {
      timeStampsByNetworkElement.put(targetName, actualTimeStamp);
    }
  }

  private String getTargetName(JsonObject convertedObject) {
    return convertedObject
        .get("ietf-yang-patch:yang-patch")
        .getAsJsonObject()
        .get("edit")
        .getAsJsonArray()
        .get(0)
        .getAsJsonObject()
        .get("target")
        .getAsString();
  }

  private String getTimeStampForVerification(JsonObject convertedObject) {
    return convertedObject
        .get("ietf-yang-patch:yang-patch")
        .getAsJsonObject()
        .get("edit")
        .getAsJsonArray()
        .get(0)
        .getAsJsonObject()
        .get("value")
        .getAsJsonObject()
        .get("SomeNetworkFunction")
        .getAsJsonArray()
        .get(0)
        .getAsJsonObject()
        .get("attributes")
        .getAsJsonObject()
        .get("timestampForVerification")
        .getAsString();
  }

  @Override
  public void reset() {
    messageCount = 0;
    failedOrderCount = 0;
    timeStampsByNetworkElement.clear();
  }
}
