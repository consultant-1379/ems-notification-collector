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
package com.ericsson.oss.adc.emsnc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Core Application, the starting point of the application. */
@SpringBootApplication
@Slf4j
public class CoreApplication {
  /**
   * Main entry point of the application.
   *
   * @param args Command line arguments
   */
  public static void main(final String[] args) {
    SpringApplication.run(CoreApplication.class, args);
  }

  /**
   * Configuration bean for Web MVC.
   *
   * @return WebMvcConfigurer
   */
  @Bean
  public WebMvcConfigurer webConfigurer() {
    return new WebMvcConfigurer() {};
  }

  /**
   * Making a RestTemplate to use for consumption of RESTful interfaces.
   *
   * @return RestTemplate
   */
  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate() {};
  }
}
