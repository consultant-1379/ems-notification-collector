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

import com.ericsson.oss.adc.emsnc.IsolationLevel;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
@EnableKafka
@Profile({"default"})
@Slf4j
public class EmsncKafkaConfiguration {

  // TODO move to a single KafkaConfiguration class?

  @Value("${emsnc.kafka.emsnc-internal.bootstrap-servers}")
  private String emsncProducerUrl;

  @Value("${emsnc.kafka.emsnc-internal.producer-isolation}")
  private IsolationLevel emsncKafkaIsolation;

  @Bean(name = "emsnc-producer-factory")
  @SuppressWarnings("squid:S128") // intentionally not terminating cases
  public ProducerFactory<String, PollingTask> emsncProducerFactory() {
    log.info("Initializing EMSNC producer for {}", emsncKafkaIsolation);

    Map<String, Object> configProps = new HashMap<>();

    switch (emsncKafkaIsolation) {
      case IDEMPOTENT:
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
      case TRANSACTIONAL:
        configProps.put(
            ProducerConfig.TRANSACTIONAL_ID_CONFIG, "emsnc-" + UUID.randomUUID().toString());
        configProps.put(ProducerConfig.CLIENT_ID_CONFIG, "emsnc-" + UUID.randomUUID().toString());
      default:
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, emsncProducerUrl);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    }

    DefaultKafkaProducerFactory<String, PollingTask> producerFactory =
        new DefaultKafkaProducerFactory<>(configProps);
    log.info(
        "Created producer factory for EMSNC Kafka (transactional: {})",
        producerFactory.transactionCapable());
    return producerFactory;
  }

  @Bean(name = "emsnc-kafka-template")
  public KafkaTemplate<String, PollingTask> emsncKafkaTemplate(
      @Qualifier("emsnc-producer-factory")
          ProducerFactory<String, PollingTask> emsncProducerFactory) {
    KafkaTemplate<String, PollingTask> kafkaTemplate = new KafkaTemplate<>(emsncProducerFactory);
    log.debug(
        "Created Kafka template for EMSNC Kafka (transactional: {})",
        kafkaTemplate.isTransactional());
    return kafkaTemplate;
  }
}
