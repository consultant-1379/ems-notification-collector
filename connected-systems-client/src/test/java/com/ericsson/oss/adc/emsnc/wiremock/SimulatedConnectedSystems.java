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
package com.ericsson.oss.adc.emsnc.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;

import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.SneakyThrows;

@Data
@Builder
public class SimulatedConnectedSystems implements SimulatedConnectedSystemsRemote {

  @Singular("enm")
  public List<SimulatedEnm> enmList;

  @Builder.Default private Integer port = null;

  private WireMockServer wireMockServer;

  private final UUID subsystemTypesStubUUID =
      UUID.nameUUIDFromBytes("subsystemTypes".getBytes(StandardCharsets.UTF_8));
  private final UUID subsystemsStubUUID =
      UUID.nameUUIDFromBytes("subsystems".getBytes(StandardCharsets.UTF_8));

  @Override
  @SneakyThrows
  public SimulatedConnectedSystems start() {
    WireMockConfiguration wireMockConfiguration =
        options()
            .extensions(
                new ResponseTemplateTransformer(false),
                new SubsystemsResponseTransformer(this::getEnmList))
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
    createGetSubsystemTypesStub();
    createGetSubsystemsStub();
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

  public URI getUri() {
    if (wireMockServer.isRunning()) {
      return URI.create("http://localhost:" + wireMockServer.port());
    } else if (port != null) {
      return URI.create("http://localhost:" + port);
    } else {
      throw new IllegalStateException(
          "Wiremock server (dynamic port) for 'Connected Systems' is not running");
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
  public void reset() {
    if (wireMockServer == null || !wireMockServer.isRunning()) {
      start();
    }
  }

  @SneakyThrows
  public void createGetSubsystemTypesStub() {
    Handlebars handlebars = new Handlebars();
    Template subsystemsTemplate = handlebars.compile("/templates/subsystem-types.json");
    String body = subsystemsTemplate.apply(this);

    wireMockServer.stubFor(
        get(urlEqualTo("/subsystem-manager/v1/subsystem-types"))
            .withId(subsystemTypesStubUUID)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Transfer-Encoding", "chunked")
                    .withHeader("Date", "{{now format='EEE, d MMM yyyy HH:mm:ss z'}}")
                    .withBody(body)
                    .withTransformers("response-template")));
  }

  @SneakyThrows
  public void createGetSubsystemsStub() {
    wireMockServer.stubFor(
        get(urlPathEqualTo("/subsystem-manager/v1/subsystems"))
            .withId(subsystemsStubUUID)
            .withQueryParam("filters", equalTo("{\"subsystemType\":\"DomainManager\"}"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("total", "1")
                    .withHeader("Content-Type", "application/json;charset=UTF-8")
                    .withHeader("Date", "{{now format='EEE, d MMM yyyy HH:mm:ss z'}}")
                    .withTransformers("response-template", SubsystemsResponseTransformer.NAME)));
  }
}
