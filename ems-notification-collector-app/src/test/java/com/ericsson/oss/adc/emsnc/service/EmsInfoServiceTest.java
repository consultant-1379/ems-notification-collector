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
package com.ericsson.oss.adc.emsnc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import com.ericsson.oss.adc.emsnc.processing.EmsInfoService;
import com.ericsson.oss.adc.emsnc.processing.data.EnmInfo;
import com.ericsson.oss.adc.emsnc.wiremock.SimulatedConnectedSystems;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@Slf4j
public class EmsInfoServiceTest {

  private EmsInfoService emsInfoService = new EmsInfoService();
  private SimulatedConnectedSystems simulatedConnectedSystems;
  private List<SimulatedEnm> enmList;

  private void startSimulatedConnectedSystems(int numberOfEnms) {
    createEnmList(numberOfEnms);
    simulatedConnectedSystems =
        SimulatedConnectedSystems.builder().enmList(enmList).build().start();
    ReflectionTestUtils.setField(emsInfoService, "connectedSystemsHost", "localhost");
    ReflectionTestUtils.setField(
        emsInfoService,
        "connectedSystemsPort",
        simulatedConnectedSystems.getWireMockServer().port());
  }

  private void createEnmList(int numberOfEnms) {
    enmList = new ArrayList<>();
    for (int i = 0; i < numberOfEnms; i++) {
      enmList.add(SimulatedEnm.builder().name("enm-" + i).build().start());
    }
  }

  @AfterEach
  public void teardown() {
    simulatedConnectedSystems.shutdown();
    enmList.forEach(SimulatedEnm::shutdown);
  }

  @Test
  public void testGetEnmList() {
    int numberOfEnms = 2;
    startSimulatedConnectedSystems(numberOfEnms);

    List<EnmInfo> enmInfoList = emsInfoService.getCurrentListOfEnmInstances();

    assertEquals(numberOfEnms, enmInfoList.size());
    for (int i = 0; i < numberOfEnms; i++) {
      assertEquals("enm-" + i, enmInfoList.get(i).getName());
    }
  }
}
