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

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ericsson.oss.adc.emsnc.client.enm.EnmClient;
import com.ericsson.oss.adc.emsnc.client.enm.model.EnmEventsResponse;
import com.ericsson.oss.adc.emsnc.client.enm.model.Event;
import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmCommandResponseData;
import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmNodeData;
import com.ericsson.oss.adc.emsnc.client.enm.test.TestData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Slf4j
class EnmClientWireMockTest {
  public static final String INVALID_TARGET_NAME = "invalidTargetName";
  private static final String TEST_MOFDN =
      "SubNetwork=Europe,SubNetwork=Ireland,MeContext=CORE13R6675057,ManagedElement=CORE13R6675057,SomeNetworkFunction=1";

  private EnmClient enmClient;
  private SimulatedEnm simulatedEnm;

  @BeforeEach
  public void setUp() {
    Map<String, Integer> targets = TestData.buildNeTypesMap();

    simulatedEnm = SimulatedEnm.builder().targets(targets).build().start();

    Retrofit enmRetrofit =
        createRetrofit(simulatedEnm.getEmsCredentials().getLocation().toString() + "/");
    enmClient = enmRetrofit.create(EnmClient.class);
  }

  @AfterEach
  public void teardown() {
    simulatedEnm.shutdown();
  }

  private Retrofit createRetrofit(String baseUrl) {
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .client(createHttpClient())
        .build();
  }

  private OkHttpClient createHttpClient() {
    HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
    interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

    return new OkHttpClient()
        .newBuilder()
        .addInterceptor(interceptor)
        .followRedirects(false)
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build();
  }

  @Test
  void testAuthFail() throws IOException {
    Map<String, String> loginData = new HashMap<>();
    loginData.put("IDToken1", "badAdmin");
    loginData.put("IDToken2", "badPassword");
    Response<ResponseEntity<String>> response = enmClient.login(loginData).execute();

    assertEquals(HttpStatus.UNAUTHORIZED.value(), response.code());

    simulatedEnm.verify(
        postRequestedFor(urlEqualTo("/login?IDToken1=badAdmin&IDToken2=badPassword")));
  }

  @Test
  void testAuthSuccess() throws IOException {
    Map<String, String> loginData = new HashMap<>();
    loginData.put("IDToken1", "administrator");
    loginData.put("IDToken2", "TestPassw0rd");
    Response<ResponseEntity<String>> response = enmClient.login(loginData).execute();

    assertEquals(HttpStatus.OK.value(), response.code());

    simulatedEnm.verify(
        postRequestedFor(urlEqualTo("/login?IDToken1=administrator&IDToken2=TestPassw0rd")));
  }

  @Test
  void testGetEvents() throws IOException {
    Response<EnmEventsResponse> enmEventsResponse =
        enmClient
            .getConfigEvents(getAuthToken(), "eventDetectionTimestamp asc", buildClauses())
            .execute();

    assertEquals(HttpStatus.OK.value(), enmEventsResponse.code());
    assertNotNull(enmEventsResponse.body());
    assertNotNull(enmEventsResponse.body().getEvents());
    assertFalse(enmEventsResponse.body().getEvents().isEmpty());
    Event event = enmEventsResponse.body().getEvents().get(0);
    assertEquals("2021-04-27T09:06:57.100Z", event.getEventDetectionTimestamp());
    assertEquals("2021-04-27T09:06:58.105Z", event.getEventRecordTimestamp());
    assertEquals("CORE13R6675057", event.getTargetName());
    assertEquals(TEST_MOFDN, event.getMoFDN());
    assertEquals("CCP", event.getNewAttributeValues().get(0).get("platformType"));
    assertEquals("18.Q4-J.2.300", event.getNewAttributeValues().get(0).get("ossModelIdentity"));
    assertEquals("MeContext=LTE02ERBS00001", event.getNewAttributeValues().get(0).get("ossPrefix"));
    assertNotNull(event.getNewAttributeValues().get(0).get("neProductVersion"));

    simulatedEnm.verify(
        getRequestedFor(
            urlMatching(
                "\\/config-mgmt\\/event\\/events\\?orderBy=eventDetectionTimestamp\\%20asc\\&filterClauses=.*")));
  }

  @NotNull
  private String buildClauses() {
    return "[{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"gte\",\"attrValue\":\"2021-04-27T09:06:58.100Z\",\"attrValues\":null},{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"lt\",\"attrValue\":\"2021-04-27T09:06:59.000Z\",\"attrValues\":null},{\"attrName\":\"targetName\",\"operator\":\"eq\",\"attrValue\":null,\"attrValues\":[\"CORE13R6675057\",\"CORE13R6675058\",\"CORE13R6675059\",\"CORE13R6675060\",\"CORE13R6675061\",\"CORE13R6675062\",\"CORE13R6675063\",\"CORE13R6675064\",\"CORE13R6675065\",\"CORE13R6675066\",\"CORE13R6675067\",\"CORE13R6675068\",\"CORE13R6675069\"]}]";
  }

  @Test
  void testGetEventsOrderByDesc() throws IOException {
    Response<EnmEventsResponse> enmEventsResponse =
        enmClient
            .getConfigEvents(
                getAuthToken(),
                "eventDetectionTimestamp desc",
                "[{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"gte\",\"attrValue\":\"2021-04-27T09:06:58.100Z\",\"attrValues\":null},{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"lt\",\"attrValue\":\"2021-04-27T09:06:59.000Z\",\"attrValues\":null},{\"attrName\":\"targetName\",\"operator\":\"eq\",\"attrValue\":null,\"attrValues\":[\"CORE13R6675057\",\"CORE13R6675058\",\"CORE13R6675059\",\"CORE13R6675060\",\"CORE13R6675061\",\"CORE13R6675062\",\"CORE13R6675063\",\"CORE13R6675064\",\"CORE13R6675065\",\"CORE13R6675066\",\"CORE13R6675067\",\"CORE13R6675068\",\"CORE13R6675069\"]}]")
            .execute();

    assertEquals(HttpStatus.OK.value(), enmEventsResponse.code());
    assertNotNull(enmEventsResponse.body());
    assertNotNull(enmEventsResponse.body().getEvents());
    assertFalse(enmEventsResponse.body().getEvents().isEmpty());
    Event event = enmEventsResponse.body().getEvents().get(0);
    assertEquals("2021-04-27T09:07:01.186Z", event.getEventDetectionTimestamp());
    assertEquals("2021-04-27T09:07:02.191Z", event.getEventRecordTimestamp());
    assertEquals("CORE13R6675069", event.getTargetName());
    assertEquals(
        "SubNetwork=Europe,SubNetwork=Ireland,MeContext=CORE13R6675069,ManagedElement=CORE13R6675069,SomeNetworkFunction=1",
        event.getMoFDN());
    simulatedEnm.verify(
        getRequestedFor(
            urlMatching(
                "\\/config-mgmt\\/event\\/events\\?orderBy=eventDetectionTimestamp\\%20desc\\&filterClauses=.*")));
  }

  @Test
  void testGetEventsInvalidTargetNameInArray() throws IOException {
    Response<EnmEventsResponse> enmEventsResponse =
        enmClient
            .getConfigEvents(
                getAuthToken(),
                "eventDetectionTimestamp asc",
                "[{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"gte\",\"attrValue\":\"2021-04-27T09:06:58.100Z\",\"attrValues\":null},{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"lt\",\"attrValue\":\"2021-04-27T09:06:59.000Z\",\"attrValues\":null},{\"attrName\":\"targetName\",\"operator\":\"eq\",\"attrValue\":null,\"attrValues\":[\"CORE13R6675057\",\""
                    + INVALID_TARGET_NAME
                    + "\"]}]")
            .execute();

    assertEquals(HttpStatus.OK.value(), enmEventsResponse.code());
    assertNotNull(enmEventsResponse.body());
    assertNotNull(enmEventsResponse.body().getEvents());
    assertFalse(enmEventsResponse.body().getEvents().isEmpty());
    Event event = enmEventsResponse.body().getEvents().get(0);
    assertEquals("2021-04-27T09:06:57.100Z", event.getEventDetectionTimestamp());
    assertEquals("2021-04-27T09:06:58.105Z", event.getEventRecordTimestamp());
    assertEquals("CORE13R6675057", event.getTargetName());
    assertEquals(TEST_MOFDN, event.getMoFDN());

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String jsonResponse = gson.toJson(enmEventsResponse.body());
    assertFalse(jsonResponse.contains(INVALID_TARGET_NAME));

    simulatedEnm.verify(
        getRequestedFor(
            urlMatching(
                "\\/config-mgmt\\/event\\/events\\?orderBy=eventDetectionTimestamp\\%20asc\\&filterClauses=.*")));
  }

  @Test
  void testGetEventsSingleTargetName() throws IOException {
    Response<EnmEventsResponse> enmEventsResponse =
        enmClient
            .getConfigEvents(
                getAuthToken(),
                "eventDetectionTimestamp asc",
                "[{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"gte\",\"attrValue\":\"2021-04-27T09:06:58.100Z\",\"attrValues\":null},{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"lt\",\"attrValue\":\"2021-04-27T09:06:59.000Z\",\"attrValues\":null},{\"attrName\":\"targetName\",\"operator\":\"eq\",\"attrValue\":\"CORE13R6675057\",\"attrValues\":null}]")
            .execute();

    assertEquals(HttpStatus.OK.value(), enmEventsResponse.code());
    assertNotNull(enmEventsResponse.body());
    assertNotNull(enmEventsResponse.body().getEvents());
    assertFalse(enmEventsResponse.body().getEvents().isEmpty());
    Event event = enmEventsResponse.body().getEvents().get(0);
    assertEquals("2021-04-27T09:06:57.100Z", event.getEventDetectionTimestamp());
    assertEquals("2021-04-27T09:06:58.105Z", event.getEventRecordTimestamp());
    assertEquals("CORE13R6675057", event.getTargetName());
    assertEquals(TEST_MOFDN, event.getMoFDN());

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    String jsonResponse = gson.toJson(enmEventsResponse.body());
    assertFalse(jsonResponse.contains(INVALID_TARGET_NAME));

    simulatedEnm.verify(
        getRequestedFor(
            urlMatching(
                "\\/config-mgmt\\/event\\/events\\?orderBy=eventDetectionTimestamp\\%20asc\\&filterClauses=.*")));
  }

  @Test
  void testGetEventsSingleInvalidTargetName() throws IOException {
    Response<EnmEventsResponse> enmEventsResponse =
        enmClient
            .getConfigEvents(
                getAuthToken(),
                "eventDetectionTimestamp asc",
                "[{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"gte\",\"attrValue\":\"2021-04-27T09:06:58.100Z\",\"attrValues\":null},{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"lt\",\"attrValue\":\"2021-04-27T09:06:59.000Z\",\"attrValues\":null},{\"attrName\":\"targetName\",\"operator\":\"eq\",\"attrValue\":\""
                    + INVALID_TARGET_NAME
                    + "\",\"attrValues\":null}]")
            .execute();

    assertEquals(HttpStatus.OK.value(), enmEventsResponse.code());
    assertNotNull(enmEventsResponse.body());
    assertNotNull(enmEventsResponse.body().getEvents());
    assertTrue(enmEventsResponse.body().getEvents().isEmpty());

    simulatedEnm.verify(
        getRequestedFor(
            urlMatching(
                "\\/config-mgmt\\/event\\/events\\?orderBy=eventDetectionTimestamp\\%20asc\\&filterClauses=.*")));
  }

  @Test
  void testGetListOfNetworkElements() throws IOException {
    String authToken = getAuthToken();
    assertEquals(50, getNetworkElements(authToken, "RadioNode").size());
    assertEquals(150, getNetworkElements(authToken, "ERBS").size());
    assertEquals(200, getNetworkElements(authToken, "Router6675").size());
  }

  @Test
  void testGetListOfNetworkElementsTwiceReturnsSameList() throws IOException {
    String authToken = getAuthToken();
    assertEquals(
        getNetworkElements(authToken, "Router6675"), getNetworkElements(authToken, "Router6675"));
  }

  private ArrayList<EnmNodeData> getNetworkElements(String authToken, final String neType)
      throws IOException {
    String command = "cmedit get * NetworkElement --netype=" + neType;
    Response<ResponseBody> result =
        enmClient
            .sendEnmCmCommand(
                authToken,
                RequestBody.create(MediaType.parse("text/plain"), "command"),
                RequestBody.create(MediaType.parse("text/plain"), "true"),
                RequestBody.create(MediaType.parse("text/plain"), command))
            .execute();
    assertEquals(HttpStatus.CREATED.value(), result.code());
    assertNotNull(result.body());
    simulatedEnm.verify(postRequestedFor(urlEqualTo("/server-scripting/services/command")));

    String requestId = result.body().string();
    Response<EnmCommandResponseData> enmResponseData =
        enmClient.getEnmCmCommandOutput(authToken, requestId).execute();

    assertEquals(HttpStatus.OK.value(), enmResponseData.code());
    assertNotNull(enmResponseData.body());
    assertFalse(enmResponseData.body().getOutput().getElements().isEmpty());
    simulatedEnm.verify(
        getRequestedFor(
            urlMatching("/server-scripting/services/command/output/" + requestId + "/stream.*")));
    return enmResponseData.body().getOutput().getElements();
  }

  @Test
  void testAuthTokenExpire() throws IOException, InterruptedException {
    simulatedEnm.setTokenExpiration(2);
    boolean tokenIsValid = true;
    String firstAuthToken = getAuthToken();
    while (tokenIsValid) {
      Response<EnmEventsResponse> enmEventsResponse =
          enmClient
              .getConfigEvents(
                  firstAuthToken,
                  "eventDetectionTimestamp asc",
                  "[{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"gte\",\"attrValue\":\"2021-04-27T09:06:58.100Z\",\"attrValues\":null},{\"attrName\":\"eventRecordTimestamp\",\"operator\":\"lt\",\"attrValue\":\"2021-04-27T09:06:59.000Z\",\"attrValues\":null},{\"attrName\":\"targetName\",\"operator\":\"eq\",\"attrValue\":null,\"attrValues\":[\"CORE13R6675057\",\"CORE13R6675058\",\"CORE13R6675059\",\"CORE13R6675060\",\"CORE13R6675061\",\"CORE13R6675062\",\"CORE13R6675063\",\"CORE13R6675064\",\"CORE13R6675065\",\"CORE13R6675066\",\"CORE13R6675067\",\"CORE13R6675068\",\"CORE13R6675069\"]}]")
              .execute();
      if (HttpStatus.OK.value() != enmEventsResponse.code()) {
        tokenIsValid = false;
        assertEquals(HttpStatus.UNAUTHORIZED.value(), enmEventsResponse.code());
        assertNotNull(enmEventsResponse.errorBody());
        assertEquals(
            AuthTokenValidatorResponseTransformer.ERROR_MESSAGE,
            enmEventsResponse.errorBody().string());
      }
      TimeUnit.SECONDS.sleep(1);
    }
  }

  public String getAuthToken() {
    Map<String, String> loginData = new HashMap<>();
    loginData.put("IDToken1", "administrator");
    loginData.put("IDToken2", "TestPassw0rd");
    Response<ResponseEntity<String>> response = null;
    try {
      response = enmClient.login(loginData).execute();
    } catch (IOException e) {
      e.printStackTrace();
      log.error("Login failed with: " + loginData);
    }

    assertNotNull(response);
    return response.headers().get("Set-Cookie");
  }
}
