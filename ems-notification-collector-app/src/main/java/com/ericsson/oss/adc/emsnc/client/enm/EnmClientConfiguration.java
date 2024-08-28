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
package com.ericsson.oss.adc.emsnc.client.enm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnmClientConfiguration {

  @Value("${emsnc.client.enm.httpClientTrustAllCertificates}")
  private boolean trustAllCertificates;

  @Bean
  public RetrofitConfiguration retrofitConfiguration() {
    return new RetrofitConfiguration(trustAllCertificates);
  }
}
