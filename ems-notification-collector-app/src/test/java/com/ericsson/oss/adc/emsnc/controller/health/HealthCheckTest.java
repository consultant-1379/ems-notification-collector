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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ericsson.oss.adc.emsnc.CoreApplication;
import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import com.ericsson.oss.adc.emsnc.service.kafka.KafkaAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {CoreApplication.class, HealthCheck.class})
@ActiveProfiles("test")
public class HealthCheckTest {

  @MockBean(name = "dmaap-kafka-template")
  KafkaTemplate<String, YangEvent> dmaapKafka;

  @MockBean(name = "emsnc-kafka-template")
  KafkaTemplate<String, PollingTask> emsncKafka;

  @MockBean KafkaAdminService kafkaAdminService;

  @Autowired private WebApplicationContext webApplicationContext;
  private MockMvc mvc;

  @Autowired private HealthCheck health;

  @BeforeEach
  public void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  public void testGetHealthStatusOk() throws Exception {
    when(kafkaAdminService.isEmsncKafkaAvailable()).thenReturn(true);
    when(kafkaAdminService.isDmaapKafkaAvailable()).thenReturn(true);
    mvc.perform(get("/actuator/health").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json("{'status' : 'UP'}"));
  }

  @Test
  @DirtiesContext
  public void testGetHealthStatusNotOk() throws Exception {
    HealthCheckError dummySendFailedError =
        new HealthCheckError(HealthCheckError.Message.SEND_FAILED, "test-topic", "test-reason");
    health.failHealthCheck(dummySendFailedError);
    mvc.perform(get("/actuator/health").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is5xxServerError())
        .andExpect(content().json("{'status' : 'DOWN'}"));
    assertTrue(
        health
            .getErrorMessages()
            .contains(new HealthCheckError(HealthCheckError.Message.EMSNC_KAFKA_NOT_AVAILABLE)));
    assertTrue(
        health
            .getErrorMessages()
            .contains(new HealthCheckError(HealthCheckError.Message.DMAAP_KAFKA_NOT_AVAILABLE)));
    assertTrue(health.getErrorMessages().contains(dummySendFailedError));
  }
}
