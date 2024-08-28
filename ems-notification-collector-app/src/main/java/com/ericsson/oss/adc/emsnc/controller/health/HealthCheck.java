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
package com.ericsson.oss.adc.emsnc.controller.health;

import static com.ericsson.oss.adc.emsnc.controller.health.HealthCheckError.Message.DMAAP_KAFKA_NOT_AVAILABLE;
import static com.ericsson.oss.adc.emsnc.controller.health.HealthCheckError.Message.EMSNC_KAFKA_NOT_AVAILABLE;

import com.ericsson.oss.adc.emsnc.service.kafka.KafkaAdminService;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Health Check component for microservice chassis. Any internal logic can change health state of
 * the chassis.
 */
@Data
@Slf4j
@ConfigurationProperties(prefix = "info.app")
@Component
public class HealthCheck implements HealthIndicator {

  /** Errors upon health check. */
  private Set<HealthCheckError> errorMessages = new HashSet<>();

  @Value("${emsnc.kafka.emsnc-internal.bootstrap-servers}")
  private String emsncBootstrapAddress;

  @Value("${emsnc.kafka.dmaap.bootstrap-servers}")
  private String dmaapBootstrapAddress;

  @Autowired private KafkaAdminService kafkaAdminService;

  @Override
  public Health health() {
    log.trace("Invoking chassis specific health check");

    if (!kafkaAdminService.isEmsncKafkaAvailable()) {
      failHealthCheck(new HealthCheckError(EMSNC_KAFKA_NOT_AVAILABLE));
    }
    if (!kafkaAdminService.isDmaapKafkaAvailable()) {
      failHealthCheck(new HealthCheckError(DMAAP_KAFKA_NOT_AVAILABLE));
    }

    if (!errorMessages.isEmpty()) {
      log.error("Health is DOWN, reason: {}", errorMessages);
      return Health.down().withDetail("Error: ", errorMessages).build();
    }
    return Health.up().build();
  }

  /**
   * Set the error message that will cause fail health check of microservice.
   *
   * @param healthCheckError Error that causes the health check to fail.
   */
  public void failHealthCheck(HealthCheckError healthCheckError) {
    this.errorMessages.add(healthCheckError);
  }
}
