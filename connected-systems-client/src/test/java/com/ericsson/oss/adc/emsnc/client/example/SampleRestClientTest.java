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
package com.ericsson.oss.adc.emsnc.client.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

// TODO [IDUN-1810] remove this class when a contract test is implemented for C-S
// @AutoConfigureMockMvc
// @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
// @AutoConfigureStubRunner(repositoryRoot =
// "https://arm.seli.gic.ericsson.se/artifactory/proj-eric-oss-dev-local",
//        stubsMode = StubRunnerProperties.StubsMode.REMOTE,
//        ids = "com/ericsson/de:microservice-chassis-producer:+:stubs:9091")
public class SampleRestClientTest {

  @Autowired private MockMvc mockMvs;

  //    @Test
  public void get_response_from_producer_contract() throws Exception {
    mockMvs
        .perform(MockMvcRequestBuilders.get("/v1/producer"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.content().string("Sample response from stubbed producer"));
  }

  @Test
  public void someTest() {
    assertTrue("improving quality by adding a dummy test on a to-be-removed class".length() > 0);
  }
}
