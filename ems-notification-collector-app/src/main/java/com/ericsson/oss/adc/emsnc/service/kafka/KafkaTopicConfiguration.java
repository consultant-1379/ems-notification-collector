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
import com.ericsson.oss.adc.emsnc.processing.PollingTaskProcessor;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import com.ericsson.oss.adc.emsnc.service.kafka.listener.AcknowledgingTaskListener;
import com.ericsson.oss.adc.emsnc.service.kafka.listener.SimpleTaskListener;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@Slf4j
@Profile({"default"})
public class KafkaTopicConfiguration {

  public static final String EMSNC_TOPIC_NAME_PREFIX = "emsnc-internal-";

  @Value("${emsnc.kafka.emsnc-internal.concurrency-per-topic}")
  private int concurrencyPerInternalTopic;

  @Value("${emsnc.kafka.emsnc-internal.partition-count}")
  private int numberOfInternalTopicPartitions;

  @Value("${emsnc.kafka.emsnc-internal.bootstrap-servers}")
  private String emsncBootstrapAddress;

  @Value("${emsnc.kafka.emsnc-internal.topic-count}")
  private int emsncInternalTopicCount;

  @Value("${emsnc.kafka.emsnc-internal.producer-isolation}")
  private IsolationLevel emsncKafkaIsolation;

  @Value("${emsnc.kafka.emsnc-internal.consumer-thread-priority}")
  private int emsncConsumerThreadPriority;

  @Autowired private PollingTaskProcessor pollingTaskProcessor;

  @Autowired private GenericApplicationContext genericApplicationContext;

  @Autowired
  @Qualifier("polling-task-executor")
  ThreadPoolTaskExecutor exec;

  @Autowired private KafkaAdminService kafkaAdminService;
  @Autowired private PlatformTransactionManager platformTransactionManager;

  @Bean(name = "polling-task-executor")
  ThreadPoolTaskExecutor createKafkaTaskExecutor() {
    ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
    threadPoolTaskExecutor.setCorePoolSize(
        concurrencyPerInternalTopic * numberOfInternalTopicPartitions * emsncInternalTopicCount);
    threadPoolTaskExecutor.setMaxPoolSize(
        concurrencyPerInternalTopic * numberOfInternalTopicPartitions * emsncInternalTopicCount);
    threadPoolTaskExecutor.setKeepAliveSeconds(30);
    threadPoolTaskExecutor.setThreadPriority(emsncConsumerThreadPriority);
    threadPoolTaskExecutor.setAllowCoreThreadTimeOut(true);
    threadPoolTaskExecutor.setThreadNamePrefix("kafka-");
    // not sure if it's actually needed
    // threadPoolTaskExecutor.setQueueCapacity(0); // Yields a SynchronousQueue
    return threadPoolTaskExecutor;
  }

  @PostConstruct
  @SuppressWarnings("squid:S128") // intentionally not terminating cases
  public void initEmsncKafkaConsumers() {
    kafkaAdminService.createEmsncKafkaTopics();

    Map<String, Object> configProps = new HashMap<>();
    switch (emsncKafkaIsolation) {
      case IDEMPOTENT:
      case TRANSACTIONAL:
        configProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
      default:
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, emsncBootstrapAddress);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, emsncKafkaIsolation == IsolationLevel.NONE);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 60000);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "emsnc-p");
        configProps.put(
            JsonDeserializer.TRUSTED_PACKAGES, "com.ericsson.oss.adc.emsnc.processing.data");
    }

    for (int i = 0; i < emsncInternalTopicCount; ++i) {
      createConsumerForTopic(
          KafkaTopicHashService.generateTopicName(i),
          createListener(emsncKafkaIsolation),
          configProps);
    }
  }

  private MessageListener<String, PollingTask> createListener(IsolationLevel emsncKafkaIsolation) {
    if (emsncKafkaIsolation == IsolationLevel.NONE) {
      return new SimpleTaskListener(pollingTaskProcessor);
    } else {
      return new AcknowledgingTaskListener(pollingTaskProcessor);
    }
  }

  @PostConstruct
  public void initDmaapKafkaTopics() {
    kafkaAdminService.createDmaapKafkaTopics();
  }

  public void createConsumerForTopic(
      final String topic,
      final MessageListener<String, PollingTask> messageListener,
      final Map<String, Object> consumerProperties) {

    log.info("creating kafka consumer for topic {}", topic);
    // If container is already created, start and return
    ContainerProperties containerProps = new ContainerProperties(topic);
    containerProps.setPollTimeout(100);
    containerProps.setConsumerTaskExecutor(exec);
    if (emsncKafkaIsolation == IsolationLevel.IDEMPOTENT
        || emsncKafkaIsolation == IsolationLevel.TRANSACTIONAL) {
      containerProps.setTransactionManager(platformTransactionManager);
    }
    Boolean enableAutoCommit =
        (Boolean) consumerProperties.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG);
    if (!enableAutoCommit) {
      containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    }
    ConsumerFactory<String, PollingTask> factory =
        new DefaultKafkaConsumerFactory<>(consumerProperties);

    // Add ConcurrentMessageListenerContainer as a bean and let Spring handle it's lifecycle
    // This way message listener threads stop gracefully upon exit
    genericApplicationContext.registerBean(
        getConcurrentMessageListenerContainerBeanName(topic),
        ConcurrentMessageListenerContainer.class,
        () -> new ConcurrentMessageListenerContainer<>(factory, containerProps));

    ConcurrentMessageListenerContainer container =
        (ConcurrentMessageListenerContainer)
            genericApplicationContext.getBean(getConcurrentMessageListenerContainerBeanName(topic));
    container.setConcurrency(concurrencyPerInternalTopic);
    container.setupMessageListener(messageListener);
    container.start();

    log.info("created and started kafka consumer for topic {}", topic);
  }

  private String getConcurrentMessageListenerContainerBeanName(String topic) {
    return topic + "-" + ConcurrentMessageListenerContainer.class.getSimpleName();
  }
}
