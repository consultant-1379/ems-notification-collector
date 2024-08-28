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
package com.ericsson.oss.adc.emsnc.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.oss.adc.emsnc.controller.health.HealthCheck;
import com.ericsson.oss.adc.emsnc.controller.health.HealthCheckError;
import com.ericsson.oss.adc.emsnc.processing.EmsInfoService;
import com.ericsson.oss.adc.emsnc.processing.TargetResolvingService;
import com.ericsson.oss.adc.emsnc.processing.data.EnmInfo;
import com.ericsson.oss.adc.emsnc.processing.data.PollingSubscription;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import com.ericsson.oss.adc.emsnc.service.kafka.KafkaTopicHashService;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.ListenableFuture;

@ExtendWith(MockitoExtension.class)
public class EmsPollingJobTest {

  public static final int TOPIC_COUNT = 3;

  public static final int POLL_DELAY = 30;

  @Mock private KafkaTemplate<String, PollingTask> kafkaTemplate;
  @Mock private EmsInfoService emsInfoService;
  @Mock private TargetResolvingService targetResolvingService;
  @Mock private JobExecutionContext jobExecutionContext;
  @Mock private JobDataMap jobDataMap;
  @Mock private Scheduler scheduler;
  @Mock private HealthCheck healthCheck;

  @Spy private KafkaTopicHashService kafkaTopicHashService;
  @InjectMocks private EmsPollingService emsPollingService;
  @InjectMocks private EmsPollingJob emsPollingJob;

  @BeforeEach
  public void setUp() throws Exception {
    JobDetail jobDetail =
        JobBuilder.newJob(EmsPollingJob.class)
            .withIdentity("keyName", "keyGroup")
            .setJobData(jobDataMap)
            .build();

    when(scheduler.getContext()).thenReturn(mock(SchedulerContext.class));
    when(jobExecutionContext.getScheduler()).thenReturn(scheduler);
    when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail);
    when(jobExecutionContext.getScheduledFireTime()).thenReturn(new Date());
    when(jobExecutionContext.getFireTime()).thenReturn(new Date());

    doReturn(getEnmInfo()).when(jobDataMap).get(ConnectedSystemsPollingJob.JDM_KEY_ENM_INFO);
    doReturn(new PollingSubscription("neType"))
        .when(jobDataMap)
        .get(ConnectedSystemsPollingJob.JDM_KEY_SUBSCRIPTION);

    ReflectionTestUtils.setField(emsPollingService, "topicCount", TOPIC_COUNT);
    ReflectionTestUtils.setField(emsPollingService, "pollDelay", POLL_DELAY);
    ReflectionTestUtils.setField(emsPollingJob, "emsPollingService", emsPollingService);

    emsPollingJob.setRetryWaitTimeInSec(1);
  }

  @Test
  void testNoTargetNamesOnEnm() throws SchedulerException {
    doReturn(new ArrayList<String>())
        .when(targetResolvingService)
        .findTargetNames(any(EnmInfo.class), anyString());
    emsPollingJob.execute(jobExecutionContext);
    verify(kafkaTemplate, never()).send(anyString(), any(), any(), any());
  }

  @Test
  void testKafkaSendWithGeneratedTargetNames() throws Exception {
    List<String> dummyUUIDTargetNames = getDummyUUIDTargetNames(30);

    ListenableFuture<SendResult<String, PollingTask>> mockSendFuture = mock(ListenableFuture.class);
    SendResult<String, PollingTask> mockSendResult = mock(SendResult.class);
    when(mockSendFuture.get(anyLong(), any(TimeUnit.class))).thenReturn(mockSendResult);
    when(kafkaTemplate.send(anyString(), anyInt(), anyString(), any(PollingTask.class)))
        .thenReturn(mockSendFuture);
    doReturn(dummyUUIDTargetNames)
        .when(targetResolvingService)
        .findTargetNames(any(EnmInfo.class), anyString());

    emsPollingJob.execute(jobExecutionContext);

    verify(kafkaTemplate, times(3)).send(anyString(), any(), any(), any());
  }

  @Test
  void testExceptionThrownWhenSendLogToKafka() {
    List<String> dummyUUIDTargetNames = getDummyUUIDTargetNames(3);

    doReturn(dummyUUIDTargetNames)
        .when(targetResolvingService)
        .findTargetNames(any(EnmInfo.class), anyString());
    when(kafkaTemplate.send(anyString(), any(), any(), any())).thenThrow(new RuntimeException());

    JobExecutionException jobExecutionException =
        Assertions.assertThrows(
            JobExecutionException.class, () -> emsPollingJob.execute(jobExecutionContext));

    Assertions.assertTrue(jobExecutionException.refireImmediately());
    verify(healthCheck, times(1)).failHealthCheck(any(HealthCheckError.class));
  }

  @Test
  void testSendToKafkaTimeoutException() throws Exception {
    List<String> dummyUUIDTargetNames = getDummyUUIDTargetNames(30);
    doReturn(dummyUUIDTargetNames)
        .when(targetResolvingService)
        .findTargetNames(any(EnmInfo.class), anyString());

    ListenableFuture<SendResult<String, PollingTask>> mockSendFuture = mock(ListenableFuture.class);
    when(mockSendFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(TimeoutException.class);
    when(kafkaTemplate.send(anyString(), anyInt(), anyString(), any(PollingTask.class)))
        .thenReturn(mockSendFuture);

    JobExecutionException jobExecutionException =
        Assertions.assertThrows(
            JobExecutionException.class, () -> emsPollingJob.execute(jobExecutionContext));

    Assertions.assertTrue(jobExecutionException.refireImmediately());
    verify(healthCheck, times(1)).failHealthCheck(any(HealthCheckError.class));
  }

  @Test
  void testTwoHoursPassedSinceTimeout() throws Exception {
    Date threeHoursEarlier = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3));
    when(jobExecutionContext.getScheduledFireTime()).thenReturn(threeHoursEarlier);

    List<String> dummyUUIDTargetNames = getDummyUUIDTargetNames(30);
    doReturn(dummyUUIDTargetNames)
        .when(targetResolvingService)
        .findTargetNames(any(EnmInfo.class), anyString());

    ListenableFuture<SendResult<String, PollingTask>> mockSendFuture = mock(ListenableFuture.class);
    when(mockSendFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(TimeoutException.class);
    when(kafkaTemplate.send(anyString(), anyInt(), anyString(), any(PollingTask.class)))
        .thenReturn(mockSendFuture);

    JobExecutionException jobExecutionException =
        Assertions.assertThrows(
            JobExecutionException.class, () -> emsPollingJob.execute(jobExecutionContext));

    Assertions.assertFalse(jobExecutionException.refireImmediately());
    verify(healthCheck, times(1)).failHealthCheck(any(HealthCheckError.class));
  }

  private EnmInfo getEnmInfo() {
    EnmInfo enmInfo = new EnmInfo();
    enmInfo.setName("enmName");
    return enmInfo;
  }

  private List<String> getDummyUUIDTargetNames(int count) {
    List<String> targetNames = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      targetNames.add(UUID.nameUUIDFromBytes(("target" + i).getBytes()).toString());
    }
    return targetNames;
  }
}
