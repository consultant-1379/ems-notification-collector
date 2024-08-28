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
package com.ericsson.oss.adc.emsnc.client.enm.test;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class TestData {
  private TestData() {
    // intentionally empty
  }

  @NotNull
  public static Map<String, Integer> buildNeTypesMap() {
    Map<String, Integer> targets = new HashMap<>();
    targets.put("RadioNode", 50);
    targets.put("ERBS", 150);
    targets.put("Router6675", 200);
    return targets;
  }
}
