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

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
public class HealthCheckError {
  private final Message message;
  @Singular private final List<String> parameters;

  public HealthCheckError(Message message, String... parameters) {
    this.message = message;
    this.parameters = List.of(parameters);
  }

  @Override
  public String toString() {
    return String.format(message.errorMessage, parameters.toArray());
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) {
      return true;
    }
    if (object == null) {
      return false;
    }
    if (this.getClass() == object.getClass()) {
      HealthCheckError otherHealthCheckError = (HealthCheckError) object;
      return message.equals(otherHealthCheckError.message)
          && parameters.equals(otherHealthCheckError.parameters);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return message.hashCode() + parameters.hashCode();
  }

  public enum Message {
    DMAAP_KAFKA_NOT_AVAILABLE("DMaaP Kafka is not available."),
    EMSNC_KAFKA_NOT_AVAILABLE("EMSNC Kafka is not available."),
    SEND_FAILED("Sending message to %s failed, reason: %s");

    private final String errorMessage;

    Message(String errorMessage) {
      this.errorMessage = errorMessage;
    }
  }
}
