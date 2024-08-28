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

import com.ericsson.oss.adc.emsnc.client.enm.model.EnmEventsResponse;
import com.ericsson.oss.adc.emsnc.client.enm.model.Event;
import com.ericsson.oss.adc.emsnc.client.enm.model.FilterClause;
import com.ericsson.oss.adc.emsnc.client.enm.model.Links;
import com.ericsson.oss.adc.emsnc.client.enm.model.Self;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class EventResponseTransformer extends ResponseTransformer {
  public static final String NAME = "event-transformer";
  private final Supplier<Map<Instant, Boolean>> eventCycleMarkers;
  private final Supplier<Long> maxEventCycles;
  private final Supplier<Integer> eventsPerCycle;

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Response transform(
      Request request, Response response, FileSource fileSource, Parameters parameters) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    String eventsResponse;
    String filterClausesParam = request.queryParameter("filterClauses").firstValue();
    String orderByParam = request.queryParameter("orderBy").firstValue();

    if (!isOrderByParamCorrect(orderByParam)) {
      return Response.notConfigured();
    }

    try {
      List<FilterClause> filterClauses =
          gson.fromJson(filterClausesParam, new TypeToken<List<FilterClause>>() {}.getType());

      FilterClause targetNameFilter = findFilterClauseByAttrName("targetName", filterClauses);
      FilterClause timestampFilter =
          findFilterClauseByAttrName("eventRecordTimestamp", filterClauses);

      if (targetNameFilter != null && timestampFilter != null) {
        eventsResponse =
            gson.toJson(
                createEventsResponse(
                    getTargetNames(targetNameFilter),
                    timestampFilter.getAttrValue(),
                    orderByParam));
      } else {
        log.error("Wrong or missing targetName or eventRecordTimestamp.");
        return Response.notConfigured();
      }
    } catch (JsonSyntaxException | DateTimeParseException e) {
      e.printStackTrace();
      return Response.notConfigured();
    }

    return Response.Builder.like(response).but().body(eventsResponse).build();
  }

  private boolean isOrderByParamCorrect(String orderByParam) {
    String[] orderByParamValues = orderByParam.split(" ");
    if (orderByParamValues.length == 2
        && orderByParamValues[0].equalsIgnoreCase("eventDetectionTimestamp")
        && (orderByParamValues[1].equals("asc") || orderByParamValues[1].equals("desc"))) {
      return true;
    }
    log.error("orderBy parameter is not eventDetectionTimestamp (asc|desc).");
    return false;
  }

  private List<String> getTargetNames(FilterClause filterClause) {
    if (filterClause.getAttrValues() != null && !filterClause.getAttrValues().isEmpty()) {
      return filterClause.getAttrValues();
    }
    return Collections.singletonList(filterClause.getAttrValue());
  }

  private FilterClause findFilterClauseByAttrName(
      String attrName, List<FilterClause> filterClauses) {
    return filterClauses.stream()
        .filter(filterClause -> filterClause.getAttrName().equals(attrName))
        .findFirst()
        .orElse(null);
  }

  private EnmEventsResponse createEventsResponse(
      List<String> targetNames, String queryTimestamp, String orderBy)
      throws DateTimeParseException {
    Instant queryInstant = Instant.parse(queryTimestamp);
    EnmEventsResponse response = new EnmEventsResponse();

    response.setLinks(createLinks());
    response.setStatus("COMPLETE");

    assureCurrentCycleMarked(queryInstant);

    if (shouldGenerateEventsForCycle(queryInstant)) {
      List<Event> events = new ArrayList<>();
      int timeBetweenEvents = 5000 / (eventsPerCycle.get() + 1);
      for (int i = 0; i < eventsPerCycle.get(); i++) {
        for (String targetName : targetNames) {
          if (!targetName.equals(EnmClientWireMockTest.INVALID_TARGET_NAME)) {
            events.add(createEvent(queryInstant, targetName));
          }
        }
        queryInstant = queryInstant.plusMillis(timeBetweenEvents);
      }
      if (orderBy.contains("desc")) {
        Collections.reverse(events);
      }
      response.setEvents(events);
    } else {
      response.setEvents(Collections.emptyList());
    }
    return response;
  }

  private Boolean shouldGenerateEventsForCycle(Instant queryInstant) {
    return eventCycleMarkers.get().get(queryInstant);
  }

  private synchronized void assureCurrentCycleMarked(Instant queryInstant) {
    if (!eventCycleMarkers.get().keySet().contains(queryInstant)) {
      if (maxEventCycles.get() > getActiveEventCycleCount()) {
        eventCycleMarkers.get().put(queryInstant, true);
      } else {
        eventCycleMarkers.get().put(queryInstant, false);
      }
    }
  }

  private long getActiveEventCycleCount() {
    return eventCycleMarkers.get().entrySet().stream().filter(e -> e.getValue()).count();
  }

  private Event createEvent(Instant eventTimestamp, String targetName) {
    Event event = new Event();
    event.setId(
        UUID.nameUUIDFromBytes((eventTimestamp + targetName).getBytes(StandardCharsets.UTF_8))
            .toString());
    event.setEventDetectionTimestamp(eventTimestamp.minusSeconds(1).toString());
    String eventRecordTimestamp = eventTimestamp.plusMillis(5).toString();
    event.setEventRecordTimestamp(eventRecordTimestamp);
    event.setTargetName(targetName);
    event.setMoClass("SomeNetworkFunction");
    event.setModelNamespace("OSS_NE_CM_DEF");
    event.setModelVersion("1.0.1");
    event.setMoFDN(
        "SubNetwork=Europe,SubNetwork=Ireland,MeContext="
            + event.getTargetName()
            + ",ManagedElement="
            + event.getTargetName()
            + ",SomeNetworkFunction=1");

    Map<String, Object> newAttributeValues = new HashMap<>();
    newAttributeValues.put("timestampForVerification", eventRecordTimestamp);
    newAttributeValues.put("platformType", "CCP");
    newAttributeValues.put("ossModelIdentity", "18.Q4-J.2.300");
    newAttributeValues.put("ossPrefix", "MeContext=LTE02ERBS00001");

    Map<String, Object> additionalProperties = new HashMap<>();
    additionalProperties.put("identity", "CXP102051/27");
    additionalProperties.put("revision", "R28J01");

    newAttributeValues.put("neProductVersion", additionalProperties);

    event.setNewAttributeValues(new ArrayList<>());
    event.getNewAttributeValues().add(newAttributeValues);

    Random random = new Random();
    random.setSeed(eventTimestamp.hashCode() / 3 + targetName.hashCode() / 3);
    if (random.nextBoolean()) {
      event.setOperationType("UPDATE");
    } else {
      event.setOperationType("CREATE");
    }

    return event;
  }

  private Links createLinks() {
    Self self = new Self();
    self.setHref("/config-mgmt/event/events/");
    Links links = new Links();
    links.setSelf(self);
    return links;
  }
}
