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

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmNodeData;
import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
public class EnmClientServiceTest {

  private SimulatedEnm simulatedEnm;
  private EnmClientService enmClientService;

  @BeforeEach
  public void setUp() {
    simulatedEnm = SimulatedEnm.builder().build().start();

    RetrofitConfiguration retrofitConfiguration = new RetrofitConfiguration(true);
    enmClientService = new EnmClientService(retrofitConfiguration);
  }

  @AfterEach
  public void teardown() {
    simulatedEnm.shutdown();
  }

  @Test
  void testGetNetworkElements() {
    EmsCredentials emsCredentials = simulatedEnm.getEmsCredentials();

    List<EnmNodeData> neList =
        enmClientService.getEnmClient(emsCredentials).getNetworkElements("RadioNode");
    assertFalse(neList.isEmpty());
    log.info("Network element list size: {}", neList.size());
  }

  @Test
  void testGetClientsInParallel() throws InterruptedException {
    EmsCredentials emsCredentials = simulatedEnm.getEmsCredentials();

    int threads = 15;
    CountDownLatch starter = new CountDownLatch(1);
    CountDownLatch stopper = new CountDownLatch(threads);
    List<Boolean> shouldFail = Collections.synchronizedList(new LinkedList<>());

    for (int i = 0; i < threads; ++i) {
      new Thread(
              () -> {
                try {
                  starter.await(60, TimeUnit.SECONDS);
                  LoginHandlingEnmClient client = enmClientService.getEnmClient(emsCredentials);
                  client.getNetworkElements("RadioNode");
                  shouldFail.add(false);
                  stopper.countDown();
                } catch (InterruptedException e) {
                  shouldFail.add(true);
                }
              })
          .start();
    }
    starter.countDown();
    assertTrue(stopper.await(60, TimeUnit.SECONDS), "All threads should have finished");
    assertEquals(threads, shouldFail.size());
    shouldFail.forEach(Assertions::assertFalse);
    simulatedEnm.verify(1, postRequestedFor(urlMatching("/login.*")));
  }

  @Test
  void testRefreshingToken() {
    EmsCredentials emsCredentials = simulatedEnm.getEmsCredentials();

    LoginHandlingEnmClient client = enmClientService.getEnmClient(emsCredentials);
    assertFalse(client.getNetworkElements("RadioNode").isEmpty());

    simulatedEnm.expireAllTokens();

    assertFalse(client.getNetworkElements("RadioNode").isEmpty());

    simulatedEnm.verify(2, postRequestedFor(urlMatching("/login.*")));
  }

  @Test
  void testRefreshingTokenInParallel() throws InterruptedException {
    simulatedEnm.setTokenExpiration(5);
    EmsCredentials emsCredentials = simulatedEnm.getEmsCredentials();

    int threads = 25;
    CountDownLatch starter = new CountDownLatch(1);
    CountDownLatch firstRoundStopper = new CountDownLatch(threads);
    CountDownLatch tokenExpires = new CountDownLatch(1);
    CountDownLatch secondRoundStopper = new CountDownLatch(threads);

    for (int i = 0; i < threads; ++i) {
      new Thread(
              () -> {
                try {
                  log.info("STARTER WAITING");
                  starter.await(60, TimeUnit.SECONDS);
                  LoginHandlingEnmClient client = enmClientService.getEnmClient(emsCredentials);
                  client.getNetworkElements("RadioNode");
                  firstRoundStopper.countDown();

                  tokenExpires.await(60, TimeUnit.SECONDS);
                  client.getNetworkElements("RadioNode");
                  secondRoundStopper.countDown();
                } catch (InterruptedException e) {
                  // intentionally empty
                }
              })
          .start();
    }
    starter.countDown();
    assertTrue(firstRoundStopper.await(60, TimeUnit.SECONDS), "All threads should have finished");

    log.info("Passed first round, expiring auth token");
    simulatedEnm.expireAllTokens();

    tokenExpires.countDown();
    assertTrue(secondRoundStopper.await(60, TimeUnit.SECONDS), "All threads should have finished");

    simulatedEnm.verify(2, postRequestedFor(urlMatching("/login.*")));
  }
}
