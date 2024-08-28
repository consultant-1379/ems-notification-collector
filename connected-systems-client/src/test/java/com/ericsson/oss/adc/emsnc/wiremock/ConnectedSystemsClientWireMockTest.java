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

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ericsson.oss.adc.emsnc.client.cs.ReadSubsystemTypesApi;
import com.ericsson.oss.adc.emsnc.client.cs.SubsystemsReadingApi;
import com.ericsson.oss.adc.emsnc.client.cs.model.SubsystemType;
import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Slf4j
public class ConnectedSystemsClientWireMockTest {

  private final List<SimulatedEnm> enmList = new ArrayList<>();
  private SimulatedConnectedSystems simulatedConnectedSystems;
  private SubsystemsReadingApi subsystemsReadingApi;
  private ReadSubsystemTypesApi subsystemTypesApi;

  @BeforeEach
  public void setUp() {
    simulatedConnectedSystems = createSimulatedConnectedSystems(1);

    Retrofit connectedSystemsRetrofit =
        createRetrofit(simulatedConnectedSystems.getUri().toString() + "/subsystem-manager/v1/");
    subsystemsReadingApi = connectedSystemsRetrofit.create(SubsystemsReadingApi.class);
    subsystemTypesApi = connectedSystemsRetrofit.create(ReadSubsystemTypesApi.class);
  }

  public SimulatedConnectedSystems createSimulatedConnectedSystems(int numberOfEnms) {
    for (int i = 0; i < numberOfEnms; i++) {
      enmList.add(SimulatedEnm.builder().build().start());
    }
    return SimulatedConnectedSystems.builder().enmList(enmList).build().start();
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

  @AfterEach
  public void teardown() {
    simulatedConnectedSystems.shutdown();
  }

  @Test
  void testGetSubsystemTypes() throws Exception {
    Response<List<SubsystemType>> response = subsystemTypesApi.getSubsystemTypes(null).execute();

    assertEquals(HttpStatus.OK.value(), response.code());
    assertNotNull(response.body());
    assertTrue(response.body().toString().contains("DomainManager"));
    simulatedConnectedSystems.verify(
        getRequestedFor(urlEqualTo("/subsystem-manager/v1/subsystem-types")));
  }

  @Test
  void testGetSubsystems() throws Exception {
    Response<Object> response =
        subsystemsReadingApi
            .getSubsystems(
                null, null, null, null, null, "{\"subsystemType\":\"DomainManager\"}", null, null)
            .execute();

    assertEquals(HttpStatus.OK.value(), response.code());
    assertNotNull(response.body());
    for (SimulatedEnm simulatedEnm : enmList) {
      assertTrue(response.body().toString().contains(simulatedEnm.getUri().toString()));
    }
    simulatedConnectedSystems.verify(
        getRequestedFor(urlPathEqualTo("/subsystem-manager/v1/subsystems")));
  }
}
