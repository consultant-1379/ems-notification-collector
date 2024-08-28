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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ericsson.oss.adc.emsnc.client.enm.EnmClientService;
import com.ericsson.oss.adc.emsnc.client.enm.RetrofitConfiguration;
import com.ericsson.oss.adc.emsnc.client.enm.test.TestData;
import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import com.ericsson.oss.adc.emsnc.processing.data.EnmInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TargetResolvingServiceTest {
  private SimulatedEnm simulatedEnm;
  private TargetResolvingService targetResolvingService;
  private EnmInfo enmInfo;
  private Map<String, Integer> targets;

  @BeforeEach
  public void setUp() {
    targets = TestData.buildNeTypesMap();
    simulatedEnm = SimulatedEnm.builder().targets(targets).build().start();
    enmInfo =
        EnmInfo.builder()
            .name(simulatedEnm.getName())
            .enmUser(simulatedEnm.getUser())
            .enmUri(simulatedEnm.getUri())
            .enmPassword(simulatedEnm.getPassword())
            .build();

    RetrofitConfiguration retrofitConfiguration = new RetrofitConfiguration(true);
    EnmClientService enmClientService = new EnmClientService(retrofitConfiguration);
    targetResolvingService = new TargetResolvingService(enmClientService);
  }

  @AfterEach
  public void teardown() {
    simulatedEnm.shutdown();
  }

  @Test
  void testGetNetworkElements() {
    targets
        .keySet()
        .forEach(
            neType -> {
              List<String> targetList = targetResolvingService.findTargetNames(enmInfo, neType);
              assertEquals(targets.get(neType), targetList.size());
            });
  }

  @Test
  void testGetNetworkElementsIsDeterministic() {

    List<String> firstCall = targetResolvingService.findTargetNames(enmInfo, "RadioNode");
    List<String> secondCall = targetResolvingService.findTargetNames(enmInfo, "RadioNode");
    // no duplications
    assertEquals(targets.get("RadioNode"), new HashSet<>(firstCall).size());
    assertEquals(firstCall, secondCall);
  }
}
