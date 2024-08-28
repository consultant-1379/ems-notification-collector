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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {CoreApplication.class, CoreApplicationTest.class})
@ActiveProfiles("test")
public class CoreApplicationTest {

  @MockBean(name = "dmaap-kafka-template")
  KafkaTemplate<String, YangEvent> dmaapKafka;

  @MockBean(name = "emsnc-kafka-template")
  KafkaTemplate<String, PollingTask> emsncKafka;

  @Autowired private WebApplicationContext webApplicationContext;
  private MockMvc mvc;

  @Value("${info.app.description}")
  private String description;

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  @BeforeEach
  public void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  public void metrics_available() throws Exception {
    final MvcResult result =
        mvc.perform(get("/actuator/prometheus").contentType(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andReturn();
    Assertions.assertTrue(
        result.getResponse().getContentAsString().contains("jvm_threads_states_threads"));
  }

  @Test
  public void info_available() throws Exception {
    final MvcResult result =
        mvc.perform(get("/actuator/info").contentType(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andReturn();
    Assertions.assertTrue(result.getResponse().getContentAsString().contains(this.description));
  }
}
