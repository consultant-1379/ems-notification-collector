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
package com.ericsson.oss.adc.emsnc.it.util;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnmRemote;
import com.ericsson.oss.adc.emsnc.it.kafka.DmaapKafkaRemote;
import com.ericsson.oss.adc.emsnc.wiremock.SimulatedConnectedSystemsRemote;
import java.lang.reflect.Proxy;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public class SimulatorProxyFactory {

  public final GenericContainer simulatorContainer;
  public final String podName;

  public SimulatorProxyFactory(GenericContainer simulatorContainer) {
    this.podName = null;
    this.simulatorContainer = simulatorContainer;
  }

  public SimulatorProxyFactory(String podName) {
    this.podName = podName;
    this.simulatorContainer = null;
  }

  public SimulatedConnectedSystemsRemote getSimulatedConnectedSystems() {
    return (SimulatedConnectedSystemsRemote)
        Proxy.newProxyInstance(
            SimulatedConnectedSystemsRemote.class.getClassLoader(),
            new Class[] {SimulatedConnectedSystemsRemote.class},
            new GroovyInvocationHandler("cs", simulatorContainer, podName));
  }

  public SimulatedEnmRemote getSimulatedEnm(int index) {
    return (SimulatedEnmRemote)
        Proxy.newProxyInstance(
            SimulatedEnmRemote.class.getClassLoader(),
            new Class[] {SimulatedEnmRemote.class},
            new GroovyInvocationHandler("enm" + index, simulatorContainer, podName));
  }

  public DmaapKafkaRemote getDmaapKafkaClient() {
    return (DmaapKafkaRemote)
        Proxy.newProxyInstance(
            DmaapKafkaRemote.class.getClassLoader(),
            new Class[] {DmaapKafkaRemote.class},
            new GroovyInvocationHandler("dmaap", simulatorContainer, podName));
  }
}
