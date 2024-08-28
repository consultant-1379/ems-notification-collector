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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;

import com.ericsson.oss.adc.emsnc.client.enm.EmsCredentials;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
public class SimulatedEnm implements SimulatedEnmRemote {
  public static final String ENM_COOKIE_NAME = "iPlanetDirectoryPro";
  public static final String DEFAULT_USER = "administrator";
  public static final String DEFAULT_PASSWORD = "TestPassw0rd";
  public static final String DEFAULT_NAME = "test-enm";
  public static final int DEFAULT_TOKEN_EXPIRATION = 300; // seconds
  public static final int DEFAULT_TARGET_COUNT = 5;

  @Builder.Default private Integer port = null;
  @Builder.Default private String name = DEFAULT_NAME;
  @Builder.Default private String user = DEFAULT_USER;
  @Builder.Default private String password = DEFAULT_PASSWORD;
  @Builder.Default private long tokenExpiration = DEFAULT_TOKEN_EXPIRATION;

  @Builder.Default
  private Map<String, Integer> targets =
      Collections.singletonMap("RadioNode", DEFAULT_TARGET_COUNT);

  @Builder.Default private Instant tokenInvalidation = Instant.EPOCH;
  @Builder.Default private long maxEventCycles = Long.MAX_VALUE;
  @Builder.Default private int eventsPerCycle = 10;

  private WireMockServer wireMockServer;
  private final Map<Instant, Boolean> eventCycleMarkers = new HashMap<>();

  @Override
  @SneakyThrows
  public SimulatedEnm start() {
    WireMockConfiguration wireMockConfiguration =
        options()
            .extensions(
                new ResponseTemplateTransformer(false),
                new EventResponseTransformer(
                    this::getEventCycleMarkers, this::getMaxEventCycles, this::getEventsPerCycle),
                new EnmCommandOutputResponseTransformer(this::getTargets),
                new AuthTokenValidatorResponseTransformer(
                    this::getTokenExpiration, this::getTokenInvalidation),
                new AuthTokenGeneratingResponseTransformer())
            .gzipDisabled(true)
            .notifier(new Slf4jNotifier(true));

    if (port == null) {
      wireMockServer = new WireMockServer(wireMockConfiguration.dynamicPort());
    } else {
      wireMockServer = new WireMockServer(wireMockConfiguration.port(port));
    }

    wireMockServer.start();
    addDefaultStubs();
    await().atMost(30, TimeUnit.SECONDS).until(wireMockServer::isRunning);
    return this;
  }

  public void addDefaultStubs() {
    createSuccessfulLoginStub();
    createUnsuccessfulLoginStub();
    createEnmCommandStub();
    createEventsStub();
  }

  @Override
  public void shutdown() {
    if (wireMockServer == null) return;
    wireMockServer.shutdown();
    await().atMost(30, TimeUnit.SECONDS).until(() -> !wireMockServer.isRunning());
    wireMockServer = null;
  }

  public void verify(RequestPatternBuilder requestPatternBuilder) {
    wireMockServer.verify(requestPatternBuilder);
  }

  public void verify(int count, RequestPatternBuilder requestPatternBuilder) {
    wireMockServer.verify(count, requestPatternBuilder);
  }

  public void verify(
      CountMatchingStrategy countMatchingStrategy, RequestPatternBuilder requestPatternBuilder) {
    wireMockServer.verify(countMatchingStrategy, requestPatternBuilder);
  }

  public int getId() {
    return Math.abs((name.hashCode() * 131) % (Integer.MAX_VALUE - 1));
  }

  public int getConnectionPropertiesId() {
    return Math.abs(((name + "connectionProperties").hashCode() * 131) % (Integer.MAX_VALUE - 1));
  }

  public URI getUri() {
    if (wireMockServer != null && wireMockServer.isRunning()) {
      return getAdvertisedUri(wireMockServer.port());
    } else if (port != null) {
      return getAdvertisedUri(port);
    } else {
      throw new IllegalStateException(
          "Wiremock server (dynamic port) for '" + name + "' is not running");
    }
  }

  @Override
  public Integer getPort() {
    if (wireMockServer.isRunning()) {
      return wireMockServer.port();
    } else {
      return port;
    }
  }

  @Override
  public void addEventCycles(int eventCycles) {
    maxEventCycles += eventCycles;
  }

  @NotNull
  private URI getAdvertisedUri(int port) {
    return URI.create(
        "http://"
            + Optional.ofNullable(System.getProperty("enmAdvertisedHost")).orElse("localhost")
            + ":"
            + port);
  }

  public void createUnsuccessfulLoginStub() {
    wireMockServer.stubFor(
        post(urlMatching("/login.*"))
            .atPriority(5)
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "text/plain; charset=UTF-8")
                    .withBody(
                        "{\"code\":\"FAILED\",\"message\":\"<title>OpenAM (Authentication Failed)</title><h3>Authentication failed.</h3>\"}")));
  }

  public void createSuccessfulLoginStub() {
    wireMockServer.stubFor(
        post(urlPathEqualTo("/login"))
            .atPriority(1)
            .withQueryParam("IDToken1", equalTo(DEFAULT_USER))
            .withQueryParam("IDToken2", equalTo(DEFAULT_PASSWORD))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/html; charset=UTF-8")
                    .withTransformers(AuthTokenGeneratingResponseTransformer.NAME)
                    .withBody("{\"code\":\"SUCCESS\",\"message\":\"Authentication Successful\"}")));
  }

  public void createEventsStub() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/config-mgmt/event/events"))
            .withQueryParam("orderBy", matching("^[a-zA-Z0-9]+\\s(asc|desc)$"))
            .withQueryParam("filterClauses", matching(".*"))
            .withCookie(ENM_COOKIE_NAME, matching(".*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/hal+json")
                    .withTransformers(
                        AuthTokenValidatorResponseTransformer.NAME,
                        EventResponseTransformer.NAME)));
  }

  public void createEnmCommandStub() {
    for (String neType : targets.keySet()) {
      String commandId = "st:" + UUID.nameUUIDFromBytes(neType.getBytes(StandardCharsets.UTF_8));
      wireMockServer.stubFor(
          post(urlPathEqualTo("/server-scripting/services/command"))
              .withCookie(ENM_COOKIE_NAME, matching(".*"))
              .withMultipartRequestBody(
                  aMultipart()
                      .withName("name")
                      .withHeader("Content-Type", equalTo("text/plain; charset=utf-8"))
                      .withBody(equalTo("command")))
              .withMultipartRequestBody(
                  aMultipart()
                      .withName("stream_output")
                      .withHeader("Content-Type", equalTo("text/plain; charset=utf-8"))
                      .withBody(equalTo("true")))
              .withMultipartRequestBody(
                  aMultipart()
                      .withName("command")
                      .withHeader("Content-Type", equalTo("text/plain; charset=utf-8"))
                      .withBody(equalTo("cmedit get * NetworkElement --netype=" + neType)))
              .willReturn(
                  aResponse()
                      .withStatus(201)
                      .withHeader(
                          "Content-Type",
                          "application/vnd.com.ericsson.oss.scripting+text;version=\"1\"")
                      .withHeader("CommandStatus", "RUNNING")
                      .withBody(commandId)
                      .withTransformers(AuthTokenValidatorResponseTransformer.NAME)));

      wireMockServer.stubFor(
          get(urlPathMatching("/server-scripting/services/command/output/" + commandId + "/stream"))
              .withHeader(
                  "Accept",
                  equalTo("application/vnd.com.ericsson.oss.scripting.command+json;VERSION=\"2\""))
              .withHeader("Accept-Encoding", containing("sdch"))
              .withCookie(ENM_COOKIE_NAME, matching(".*"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader(
                          "Content-Type",
                          "application/vnd.com.ericsson.oss.scripting.command+json;VERSION=\"2\";charset=utf-8")
                      .withTransformers(
                          AuthTokenValidatorResponseTransformer.NAME,
                          EnmCommandOutputResponseTransformer.NAME)));
    }
  }

  public EmsCredentials getEmsCredentials() {
    return new EmsCredentials(name, user, password, getUri());
  }

  @SneakyThrows
  public void expireAllTokens() {
    tokenInvalidation = Instant.now();
  }

  public long getRemainingEventCycles() {
    return maxEventCycles - countPastEventCycles();
  }

  private long countPastEventCycles() {
    return eventCycleMarkers.values().stream().filter(b -> b).count();
  }

  @Override
  public void reset(Integer defaultEnmEventsPerCycle) {
    if (wireMockServer == null || !wireMockServer.isRunning()) {
      start();
    }
    this.setEventsPerCycle(defaultEnmEventsPerCycle);
    this.maxEventCycles = countPastEventCycles();
    setTargetInstanceCount(DEFAULT_TARGET_COUNT);
  }

  @Override
  public void setTargetInstanceCount(int targetCount) {
    // Original Map might be immutable
    targets = new HashMap(targets);

    for (String neType : targets.keySet()) {
      targets.put(neType, targetCount);
    }
  }
}
