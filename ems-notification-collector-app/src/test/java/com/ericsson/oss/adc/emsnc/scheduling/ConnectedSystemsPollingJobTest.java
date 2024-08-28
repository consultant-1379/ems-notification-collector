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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.oss.adc.emsnc.processing.EmsInfoService;
import com.ericsson.oss.adc.emsnc.processing.NotificationSubscriptionService;
import com.ericsson.oss.adc.emsnc.processing.data.EnmInfo;
import com.ericsson.oss.adc.emsnc.processing.data.PollingSubscription;
import java.io.Serializable;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ConnectedSystemsPollingJobTest {

  public static final String EMS_POLLING_CRON_SCHEDULE = "10/30 * * * * ?";
  @Mock private NotificationSubscriptionService notificationSubscriptionService;

  @Mock private EmsInfoService emsInfoService;

  @Mock private SchedulerFactoryBean schedulerFactoryBean;

  @Mock private Scheduler scheduler;

  @Mock private JobExecutionContext jobExecutionContext;

  @InjectMocks private ConnectedSystemsPollingJob connectedSystemsPollingJob;

  @BeforeEach
  public void setUp() throws SchedulerException {
    when(schedulerFactoryBean.getScheduler()).thenReturn(scheduler);
    when(scheduler.getContext()).thenReturn(mock(SchedulerContext.class));
    when(jobExecutionContext.getScheduler()).thenReturn(scheduler);
    ReflectionTestUtils.setField(
        connectedSystemsPollingJob, "emsPollingCronSchedule", EMS_POLLING_CRON_SCHEDULE);
  }

  @Test
  public void testNoEnmGeneratesNoJobs() throws SchedulerException {
    when(emsInfoService.getCurrentListOfEnmInstances()).thenReturn(Collections.emptyList());
    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, never()).scheduleJob(any(), any());
  }

  @Test
  public void testNoSubscriptionGeneratesNoJobs() throws SchedulerException {
    when(emsInfoService.getCurrentListOfEnmInstances()).thenReturn(someEnms(3));
    when(notificationSubscriptionService.getPollingSubscriptions())
        .thenReturn(Collections.emptyList());
    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, never()).scheduleJob(any(), any());
  }

  @Test
  public void someJobsGenerated() throws SchedulerException {
    when(emsInfoService.getCurrentListOfEnmInstances()).thenReturn(someEnms(2));
    when(notificationSubscriptionService.getPollingSubscriptions())
        .thenReturn(someSubscriptions(2));
    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, times(4)).scheduleJob(any(), any());
  }

  @Test
  public void verifyResultJob() throws SchedulerException {
    when(emsInfoService.getCurrentListOfEnmInstances()).thenReturn(someEnms(1));
    when(notificationSubscriptionService.getPollingSubscriptions())
        .thenReturn(someSubscriptions(1));
    ArgumentCaptor<JobDetail> jobDetailArgumentCaptor = ArgumentCaptor.forClass(JobDetail.class);
    ArgumentCaptor<Trigger> triggerArgumentCaptor = ArgumentCaptor.forClass(Trigger.class);
    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler)
        .scheduleJob(jobDetailArgumentCaptor.capture(), triggerArgumentCaptor.capture());
    Trigger trigger = triggerArgumentCaptor.getValue();
    assertNotNull(trigger);
    JobDetail jobDetail = jobDetailArgumentCaptor.getValue();
    assertNotNull(jobDetail);
    jobDetail
        .getJobDataMap()
        .getWrappedMap()
        .values()
        .forEach(v -> assertTrue(v instanceof Serializable));
    assertEquals(
        someSubscriptions(1).iterator().next(),
        jobDetail.getJobDataMap().get(ConnectedSystemsPollingJob.JDM_KEY_SUBSCRIPTION));
    assertEquals(
        someEnms(1).iterator().next(),
        jobDetail.getJobDataMap().get(ConnectedSystemsPollingJob.JDM_KEY_ENM_INFO));
    assertEquals(trigger.getJobKey(), jobDetail.getKey());
  }

  private List<EnmInfo> someEnms(int count) {
    return IntStream.range(0, count)
        .mapToObj(
            i ->
                EnmInfo.builder()
                    .name("enm-" + i)
                    .enmUser("u")
                    .enmPassword("p")
                    .enmUri(createEnmUri(i))
                    .build())
        .collect(Collectors.toList());
  }

  private List<PollingSubscription> someSubscriptions(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> PollingSubscription.builder().neType("some-5g-thing").build())
        .collect(Collectors.toList());
  }

  @SneakyThrows
  private URI createEnmUri(int i) {
    return new URI("http://example-" + i + ".enm");
  }
}
