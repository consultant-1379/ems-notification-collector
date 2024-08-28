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

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class EnmClientServiceWithMultipleEnmsTest {
  private static final int NUMBER_OF_ENMS = 2;
  private EnmClientService enmClientService;
  private List<SimulatedEnm> enmSimulations;

  @BeforeEach
  public void setup() {
    enmSimulations = new ArrayList<>();
    for (int i = 0; i < NUMBER_OF_ENMS; i++) {
      SimulatedEnm enm = SimulatedEnm.builder().name("enm-" + i).build();
      enmSimulations.add(enm);
    }
    enmSimulations.forEach(SimulatedEnm::start);

    RetrofitConfiguration retrofitConfiguration = new RetrofitConfiguration(true);
    enmClientService = new EnmClientService(retrofitConfiguration);
  }

  @Test
  void testGetTargetsOnAllEnms() {
    for (SimulatedEnm simulation : enmSimulations) {
      assertFalse(
          enmClientService
              .getEnmClient(simulation.getEmsCredentials())
              .getNetworkElements("RadioNode")
              .isEmpty());
    }

    for (SimulatedEnm simulation : enmSimulations) {
      simulation.verify(1, postRequestedFor(urlMatching("/login.*")));
      simulation.verify(1, postRequestedFor(urlEqualTo("/server-scripting/services/command")));
      simulation.verify(
          1, getRequestedFor(urlMatching("/server-scripting/services/command/output/.*/stream.*")));
    }
  }
}
