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
package com.ericsson.oss.adc.emsnc.processing;

import com.ericsson.oss.adc.emsnc.processing.data.PollingSubscription;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationSubscriptionService {

  @Value("${emsnc.subscriptions.ne-types}")
  private String subscriptionNeTypes;

  public List<PollingSubscription> getPollingSubscriptions() {
    List<PollingSubscription> activeSubscriptions =
        Arrays.stream(subscriptionNeTypes.split(","))
            .map(neType -> PollingSubscription.builder().neType(neType).build())
            .collect(Collectors.toList());
    log.debug("returning subscriptions {}", activeSubscriptions);
    return activeSubscriptions;
  }
}
