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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import com.ericsson.oss.adc.emsnc.processing.EmsInfoService;
import com.ericsson.oss.adc.emsnc.processing.NotificationSubscriptionService;
import com.ericsson.oss.adc.emsnc.processing.data.PollingSubscription;
import com.ericsson.oss.adc.emsnc.wiremock.SimulatedConnectedSystems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ConnectedSystemsPollingJobEnmCachingTest {

  private SimulatedConnectedSystems simulatedConnectedSystems;

  @Spy private EmsInfoService emsInfoService = new EmsInfoService();

  @Mock private NotificationSubscriptionService notificationSubscriptionService;

  @Mock private SchedulerFactoryBean schedulerFactoryBean;

  @Mock private Scheduler scheduler;

  @Mock private JobExecutionContext jobExecutionContext;

  @InjectMocks private ConnectedSystemsPollingJob connectedSystemsPollingJob;

  @BeforeEach
  public void setUp() throws SchedulerException {
    when(schedulerFactoryBean.getScheduler()).thenReturn(scheduler);
    when(scheduler.getContext()).thenReturn(mock(SchedulerContext.class));
    when(jobExecutionContext.getScheduler()).thenReturn(scheduler);
    when(notificationSubscriptionService.getPollingSubscriptions())
        .thenReturn(
            Collections.singletonList(
                PollingSubscription.builder().neType("some-5g-thing").build()));
    ReflectionTestUtils.setField(
        connectedSystemsPollingJob,
        "emsPollingCronSchedule",
        ConnectedSystemsPollingJobTest.EMS_POLLING_CRON_SCHEDULE);
  }

  private void startConnectedSystems(int numberOfEnms) {
    simulatedConnectedSystems =
        SimulatedConnectedSystems.builder().enmList(createEnmList(numberOfEnms)).build().start();
    ReflectionTestUtils.setField(emsInfoService, "connectedSystemsHost", "localhost");
    ReflectionTestUtils.setField(
        emsInfoService,
        "connectedSystemsPort",
        simulatedConnectedSystems.getWireMockServer().port());
  }

  private List<SimulatedEnm> createEnmList(int numberOfEnms) {
    List<SimulatedEnm> enmList = new ArrayList<>();
    for (int i = 0; i < numberOfEnms; i++) {
      enmList.add(SimulatedEnm.builder().name("enm-" + i).build().start());
    }
    return enmList;
  }

  @Test
  void testEnmAddedToEmptyCS() throws SchedulerException {
    startConnectedSystems(0);

    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, never()).scheduleJob(any(), any());

    simulatedConnectedSystems.setEnmList(createEnmList(1));

    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, times(1)).scheduleJob(any(), any());
  }

  @Test
  void testSingleEnmRemovedFromCS() throws SchedulerException {
    startConnectedSystems(1);

    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, times(1)).scheduleJob(any(), any());

    simulatedConnectedSystems.setEnmList(createEnmList(0));

    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, times(1)).scheduleJob(any(), any());
  }

  @Test
  void testMutlipleEnmsAdded() throws SchedulerException {
    startConnectedSystems(2);

    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, times(2)).scheduleJob(any(), any());

    simulatedConnectedSystems.setEnmList(createEnmList(4));

    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, times(6)).scheduleJob(any(), any());
  }

  @Test
  void testMutlipleEnmsRemoved() throws SchedulerException {
    startConnectedSystems(4);

    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, times(4)).scheduleJob(any(), any());

    simulatedConnectedSystems.setEnmList(createEnmList(2));

    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, times(6)).scheduleJob(any(), any());
  }

  @Test
  void testConnectedSystemsShutDown() throws SchedulerException {
    startConnectedSystems(1);
    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, times(1)).scheduleJob(any(), any());

    simulatedConnectedSystems.shutdown();
    connectedSystemsPollingJob.execute(jobExecutionContext);
    verify(scheduler, times(1)).scheduleJob(any(), any());
    verify(scheduler, never()).deleteJob(any());
  }

  @AfterEach
  public void teardown() {
    simulatedConnectedSystems.shutdown();
    simulatedConnectedSystems.getEnmList().forEach(SimulatedEnm::shutdown);
  }
}
