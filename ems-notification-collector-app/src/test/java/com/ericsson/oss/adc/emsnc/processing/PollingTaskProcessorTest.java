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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.oss.adc.emsnc.client.enm.EmsCredentials;
import com.ericsson.oss.adc.emsnc.client.enm.EnmClientService;
import com.ericsson.oss.adc.emsnc.client.enm.RetrofitConfiguration;
import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import com.ericsson.oss.adc.emsnc.controller.health.HealthCheck;
import com.ericsson.oss.adc.emsnc.controller.health.HealthCheckError;
import com.ericsson.oss.adc.emsnc.model.yang.YangEvent;
import com.ericsson.oss.adc.emsnc.processing.data.EnmInfo;
import com.ericsson.oss.adc.emsnc.processing.data.PollingTask;
import com.ericsson.oss.adc.emsnc.scheduling.ConnectedSystemsPollingJob;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.ListenableFuture;

@ExtendWith(MockitoExtension.class)
public class PollingTaskProcessorTest {

  private SimulatedEnm simulatedEnm;

  @Spy
  private final EnmClientService enmClientService =
      new EnmClientService(new RetrofitConfiguration(true));

  @Mock private KafkaTemplate<String, YangEvent> dmaapKafkaTemplate;

  @Mock private SchedulerFactoryBean schedulerFactoryBean;

  @Mock private Scheduler scheduler;

  @Mock private JobDetail jobDetail;

  @Mock private JobDataMap jobDataMap;

  @Mock private HealthCheck healthCheck;

  @InjectMocks private PollingTaskProcessor pollingTaskProcessor;

  @BeforeEach
  public void setUp() {
    simulatedEnm = SimulatedEnm.builder().build().start();
    ReflectionTestUtils.setField(pollingTaskProcessor, "dmaapTopicName", "some-topic-name");
    when(schedulerFactoryBean.getScheduler()).thenReturn(scheduler);
  }

  @AfterEach
  public void teardown() {
    simulatedEnm.shutdown();
  }

  @Test
  public void testPolling() throws SchedulerException {
    when(scheduler.getJobDetail(any())).thenReturn(jobDetail);
    when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
    EmsCredentials.Credentials emsCredentials = simulatedEnm.getEmsCredentials().getCredentials();
    EnmInfo enmInfo =
        EnmInfo.builder()
            .name(emsCredentials.getName())
            .enmPassword(emsCredentials.getPassword())
            .enmUri(emsCredentials.getLocation())
            .enmUser(emsCredentials.getUsername())
            .build();
    when(jobDataMap.get(ConnectedSystemsPollingJob.JDM_KEY_ENM_INFO)).thenReturn(enmInfo);

    long now = Instant.now().toEpochMilli();
    PollingTask payload =
        PollingTask.builder()
            .jobKeyName("myJobKey")
            .endTimestamp(now)
            .startTimestamp(now - 30000)
            .targetName("LTE94dg2ERBS00013")
            .targetName("LTE94dg2ERBS00014")
            .targetName("LTE94dg2ERBS00015")
            .neType("RadioNode")
            .build();

    pollingTaskProcessor.receive(payload);
    verify(dmaapKafkaTemplate, times(30)).send(eq("some-topic-name"), isA(YangEvent.class));
  }

  @Test
  public void testPollingIfNoEnm() throws SchedulerException {
    when(scheduler.getJobDetail(any())).thenReturn(null);
    long now = Instant.now().toEpochMilli();
    PollingTask payload =
        PollingTask.builder()
            .jobKeyName("myJobKey")
            .endTimestamp(now)
            .startTimestamp(now - 30000)
            .targetName("LTE94dg2ERBS00013")
            .targetName("LTE94dg2ERBS00014")
            .targetName("LTE94dg2ERBS00015")
            .neType("RadioNode")
            .build();

    pollingTaskProcessor.receive(payload);
    verify(jobDetail, never()).getJobDataMap();
  }

  @Test
  void testSendToKafkaTimeoutException() throws Exception {
    ListenableFuture<SendResult<String, YangEvent>> mockSendFuture = mock(ListenableFuture.class);
    when(mockSendFuture.get(anyLong(), any(TimeUnit.class))).thenThrow(TimeoutException.class);
    when(dmaapKafkaTemplate.send(anyString(), any(YangEvent.class))).thenReturn(mockSendFuture);

    when(scheduler.getJobDetail(any())).thenReturn(jobDetail);
    when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);
    EmsCredentials.Credentials emsCredentials = simulatedEnm.getEmsCredentials().getCredentials();
    EnmInfo enmInfo =
        EnmInfo.builder()
            .name(emsCredentials.getName())
            .enmPassword(emsCredentials.getPassword())
            .enmUri(emsCredentials.getLocation())
            .enmUser(emsCredentials.getUsername())
            .build();
    when(jobDataMap.get(ConnectedSystemsPollingJob.JDM_KEY_ENM_INFO)).thenReturn(enmInfo);

    long now = Instant.now().toEpochMilli();
    PollingTask payload =
        PollingTask.builder()
            .jobKeyName("myJobKey")
            .endTimestamp(now)
            .startTimestamp(now - 30000)
            .targetName("LTE94dg2ERBS00013")
            .targetName("LTE94dg2ERBS00014")
            .targetName("LTE94dg2ERBS00015")
            .neType("RadioNode")
            .build();

    pollingTaskProcessor.receive(payload);
    verify(healthCheck, times(30)).failHealthCheck(any(HealthCheckError.class));
  }
}
