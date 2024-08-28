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
import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.service.kafka.serialization.YangEventSerializer;
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

@Configuration
@EnableKafka
@Profile({"default"})
@Slf4j
public class DmaapKafkaConfiguration {
  // TODO move to a single KafkaConfiguration class?

  @Value("${emsnc.kafka.dmaap.bootstrap-servers}")
  private String dmaapProducerUrl;

  @Value("${emsnc.kafka.dmaap.producer-isolation}")
  private IsolationLevel dmaapKafkaIsolation;

  @Bean(name = "dmaap-producer-factory")
  @SuppressWarnings("squid:S128") // intentionally not terminating cases
  public ProducerFactory<String, YangEvent> dmaapProducerFactory() {
    log.info("Initializing DMaaP producer for {}", dmaapKafkaIsolation);
    Map<String, Object> configProps = new HashMap<>();
    switch (dmaapKafkaIsolation) {
      case IDEMPOTENT:
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
      case TRANSACTIONAL:
        configProps.put(
            ProducerConfig.TRANSACTIONAL_ID_CONFIG, "dmaap-" + UUID.randomUUID().toString());
        configProps.put(ProducerConfig.CLIENT_ID_CONFIG, "dmaap-" + UUID.randomUUID().toString());
      default:
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, dmaapProducerUrl);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, YangEventSerializer.class);
    }

    DefaultKafkaProducerFactory<String, YangEvent> producerFactory =
        new DefaultKafkaProducerFactory<>(configProps);
    log.info(
        "Created producer factory for DMaaP Kafka (transactional: {})",
        producerFactory.transactionCapable());
    return producerFactory;
  }

  @Bean(name = "dmaap-kafka-template")
  public KafkaTemplate<String, YangEvent> dmaapKafkaTemplate(
      @Qualifier("dmaap-producer-factory")
          ProducerFactory<String, YangEvent> dmaapProducerFactory) {
    KafkaTemplate<String, YangEvent> kafkaTemplate = new KafkaTemplate<>(dmaapProducerFactory);
    log.debug(
        "Created Kafka template for DMaaP Kafka (transactional: {})",
        kafkaTemplate.isTransactional());
    return kafkaTemplate;
  }
}
