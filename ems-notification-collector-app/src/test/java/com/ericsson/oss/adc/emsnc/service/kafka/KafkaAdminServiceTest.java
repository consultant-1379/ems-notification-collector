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
package com.ericsson.oss.adc.emsnc.service.kafka;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {KafkaAdminService.class})
public class KafkaAdminServiceTest {

  @Value("${emsnc.kafka.emsnc-internal.bootstrap-servers}")
  private String emsncBootstrapAddress;

  @Value("${emsnc.kafka.dmaap.bootstrap-servers}")
  private String dmaapBootstrapAddress;

  @Mock private AdminClient mockEmsncAdminClient;

  @Mock private AdminClient mockDmaapAdminClient;

  @SpyBean private KafkaAdminService kafkaAdminService;

  private Set<String> emsncKafkaTopics =
      Set.of(
          "emsnc-internal-0",
          "emsnc-internal-1",
          "emsnc-internal-2",
          "emsnc-internal-3",
          "emsnc-internal-4",
          "emsnc-internal-5",
          "emsnc-internal-6",
          "emsnc-internal-7",
          "emsnc-internal-8",
          "emsnc-internal-9",
          "emsnc-internal-10",
          "emsnc-internal-11",
          "emsnc-internal-12",
          "emsnc-internal-13",
          "emsnc-internal-14");

  private Set<String> dmaapKafkaTopics = Set.of("dmaap-result-topic");

  private void createKafkaMocks(
      Set<String> kafkaTopics, AdminClient adminClient, String bootstrapAddress)
      throws InterruptedException, ExecutionException, TimeoutException {
    ListTopicsResult mockEmsncListTopicsResult = mockListTopicsResult(kafkaTopics);
    when(adminClient.listTopics()).thenReturn(mockEmsncListTopicsResult);
    when(kafkaAdminService.getKafkaAdminClient(bootstrapAddress)).thenReturn(adminClient);
  }

  private ListTopicsResult mockListTopicsResult(Set<String> kafkaTopics)
      throws InterruptedException, ExecutionException, TimeoutException {
    ListTopicsResult mockListTopicsResult = Mockito.mock(ListTopicsResult.class);
    KafkaFuture<Set<String>> mockKafkaFuture = mockNamesKafkaFuture(kafkaTopics);
    when(mockListTopicsResult.names()).thenReturn(mockKafkaFuture);
    return mockListTopicsResult;
  }

  private KafkaFuture<Set<String>> mockNamesKafkaFuture(Set<String> kafkaTopics)
      throws InterruptedException, ExecutionException, TimeoutException {
    KafkaFuture<Set<String>> mockNamesFuture = Mockito.mock(KafkaFuture.class);
    when(mockNamesFuture.get()).thenReturn(kafkaTopics);
    when(mockNamesFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(kafkaTopics);
    return mockNamesFuture;
  }

  private void mockListTopicsTimeout(AdminClient adminClient, String bootstrapAddress)
      throws InterruptedException, ExecutionException, TimeoutException {
    KafkaFuture<Set<String>> kafkaFuture = mock(KafkaFuture.class);
    when(kafkaFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(TimeoutException.class);

    ListTopicsResult listTopicsResult = mock(ListTopicsResult.class);
    when(listTopicsResult.names()).thenReturn(kafkaFuture);
    when(adminClient.listTopics()).thenReturn(listTopicsResult);

    when(kafkaAdminService.getKafkaAdminClient(bootstrapAddress)).thenReturn(adminClient);
  }

  @Test
  void testEmsncKafkaAvailable() throws Exception {
    createKafkaMocks(emsncKafkaTopics, mockEmsncAdminClient, emsncBootstrapAddress);
    assertTrue(kafkaAdminService.isEmsncKafkaAvailable());
  }

  @Test
  void testEmsncKafkaNotAvailableMissingTopics() throws Exception {
    createKafkaMocks(
        Set.of("emsnc-internal-0", "other-topic"), mockEmsncAdminClient, emsncBootstrapAddress);
    assertFalse(kafkaAdminService.isEmsncKafkaAvailable());
  }

  @Test
  void testEmsncKafkaNotAvailableTimeout() throws Exception {
    mockListTopicsTimeout(mockEmsncAdminClient, emsncBootstrapAddress);
    assertFalse(kafkaAdminService.isEmsncKafkaAvailable());
  }

  @Test
  void testDmaapKafkaAvailable() throws Exception {
    createKafkaMocks(dmaapKafkaTopics, mockDmaapAdminClient, dmaapBootstrapAddress);
    assertTrue(kafkaAdminService.isDmaapKafkaAvailable());
  }

  @Test
  void testDmaapKafkaNotAvailableMissingTopics() throws Exception {
    createKafkaMocks(Set.of("other-topic"), mockDmaapAdminClient, dmaapBootstrapAddress);
    assertFalse(kafkaAdminService.isDmaapKafkaAvailable());
  }

  @Test
  void testDmaapKafkaNotAvailableTimeout() throws Exception {
    mockListTopicsTimeout(mockDmaapAdminClient, dmaapBootstrapAddress);
    assertFalse(kafkaAdminService.isDmaapKafkaAvailable());
  }

  @Test
  void testCreateEmsncKafkaTopics() throws Exception {
    createKafkaMocks(emsncKafkaTopics, mockEmsncAdminClient, emsncBootstrapAddress);
    kafkaAdminService.createEmsncKafkaTopics();
    verify(mockEmsncAdminClient).createTopics(any(Collection.class));
  }

  @Test
  void testCreateDmaapKafkaTopics() throws Exception {
    createKafkaMocks(dmaapKafkaTopics, mockDmaapAdminClient, dmaapBootstrapAddress);
    kafkaAdminService.createDmaapKafkaTopics();
    verify(mockDmaapAdminClient).createTopics(any(Collection.class));
  }
}
