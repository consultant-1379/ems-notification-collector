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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ericsson.oss.adc.emsnc.client.enm.EmsCredentials;
import com.ericsson.oss.adc.emsnc.client.enm.EnmClient;
import com.ericsson.oss.adc.emsnc.client.enm.EnmLogin;
import com.ericsson.oss.adc.emsnc.client.enm.RetrofitConfiguration;
import com.ericsson.oss.adc.emsnc.client.enm.model.EnmEventsResponse;
import com.ericsson.oss.adc.emsnc.client.enm.model.Event;
import com.ericsson.oss.adc.emsnc.client.enm.model.FilterClause;
import com.ericsson.oss.adc.emsnc.client.enm.model.Operator;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import retrofit2.Response;
import retrofit2.Retrofit;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GetCmEventFilterTest {

  public final URI LOCATION = URI.create("https://ieatenm5439-6.athtem.eei.ericsson.se/");

  private final EmsCredentials emsCredentials =
      new EmsCredentials("", "administrator", "TestPassw0rd", LOCATION);
  private String token;
  private EnmClient enmClient;

  @SneakyThrows
  @BeforeAll
  public void setup() {
    // TODO test with wiremock
    assumeTrue(InetAddress.getByName(LOCATION.getHost()).isReachable(5000));

    RetrofitConfiguration configuration = new RetrofitConfiguration(true);
    Retrofit enmBuilder = configuration.createEnmBuilder(emsCredentials.getLocation());

    enmClient = enmBuilder.create(EnmClient.class);

    token = EnmLogin.queryForAuthenticationToken(emsCredentials, enmClient);
    assertNotNull(token);
    assertTrue(
        token.length() > emsCredentials.getCookieName().length() + 1, "auth token cannot be null");
  }

  @Test
  public void getCmEventFilter() throws IOException {
    String filterClauses = createFilterClauses();
    Response<EnmEventsResponse> response =
        enmClient.getConfigEvents(token, "eventDetectionTimestamp desc", filterClauses).execute();
    assert response.body() != null;
    List<Event> events = response.body().getEvents();
    assertTrue(events.size() > 0, "Number of events must be more than zero");
  }

  private static String createFilterClauses() {
    final List<FilterClause> clauses = new ArrayList<>();
    FilterClause filterClause =
        FilterClause.builder()
            .attrName(FilterClause.EVENT_RECORD_TIMESTAMP)
            .operator(Operator.GTE)
            .attrValue(Instant.now().minus(60, ChronoUnit.SECONDS).toString())
            .build();
    clauses.add(filterClause);
    filterClause =
        FilterClause.builder()
            .attrName(FilterClause.EVENT_RECORD_TIMESTAMP)
            .operator(Operator.LT)
            .attrValue(Instant.now().minus(30, ChronoUnit.SECONDS).toString())
            .build();
    clauses.add(filterClause);
    List<String> targetNames = new ArrayList<>();
    for (int i = 45; i < 70; i++) {
      targetNames.add("CORE13R66750" + i);
    }
    filterClause =
        FilterClause.builder()
            .attrName(FilterClause.TARGET_NAME)
            .operator(Operator.EQ)
            .attrValues(targetNames)
            .build();
    clauses.add(filterClause);
    return new Gson().toJson(clauses);
  }
}
