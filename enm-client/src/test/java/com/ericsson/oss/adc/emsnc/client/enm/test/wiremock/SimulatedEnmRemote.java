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
package com.ericsson.oss.adc.emsnc.client.enm.test.wiremock;

import lombok.SneakyThrows;

public interface SimulatedEnmRemote {
  @SneakyThrows
  SimulatedEnm start();

  void shutdown();

  Integer getPort();

  String getName();

  String getUser();

  String getPassword();

  long getMaxEventCycles();

  int getEventsPerCycle();

  void setName(String name);

  void setUser(String user);

  void setPassword(String password);

  void setMaxEventCycles(long maxEventCycles);

  void setEventsPerCycle(int eventsPerCycle);

  void addEventCycles(int eventCycles);

  long getRemainingEventCycles();

  void reset(Integer defaultEnmEventsPerCycle);

  void setTargetInstanceCount(int targetCount);
}
