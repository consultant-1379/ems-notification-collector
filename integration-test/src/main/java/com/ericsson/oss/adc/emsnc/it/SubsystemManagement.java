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
package com.ericsson.oss.adc.emsnc.it;

import com.ericsson.oss.adc.emsnc.client.cs.SubsystemsCreationAndUpdateApi;
import com.ericsson.oss.adc.emsnc.client.cs.SubsystemsDeleteApi;
import com.ericsson.oss.adc.emsnc.client.cs.SubsystemsReadingApi;
import com.ericsson.oss.adc.emsnc.client.cs.model.ConnectionProperties;
import com.ericsson.oss.adc.emsnc.client.cs.model.Subsystem;
import com.ericsson.oss.adc.emsnc.client.cs.model.SubsystemType;
import com.ericsson.oss.adc.emsnc.client.enm.test.wiremock.SimulatedEnm;
import com.google.gson.internal.LinkedTreeMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Slf4j
public class SubsystemManagement {

  private SubsystemsReadingApi subsystemsReadingApi;
  private SubsystemsCreationAndUpdateApi subsystemsCreationAndUpdateApi;
  private SubsystemsDeleteApi subsystemsDeleteApi;

  public SubsystemManagement(String host, String port) {
    Retrofit connectedSystemsRetrofit =
        createRetrofit("http://" + host + ":" + port + "/subsystem-manager/v1/");

    subsystemsReadingApi = connectedSystemsRetrofit.create(SubsystemsReadingApi.class);
    subsystemsCreationAndUpdateApi =
        connectedSystemsRetrofit.create(SubsystemsCreationAndUpdateApi.class);
    subsystemsDeleteApi = connectedSystemsRetrofit.create(SubsystemsDeleteApi.class);
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

  public Set<String> getExistingSubsystems() throws IOException {
    Response<Object> enmListResponse =
        subsystemsReadingApi
            .getSubsystems(
                null, null, null, null, null, "{\"subsystemType\":\"DomainManager\"}", null, null)
            .execute();

    List<LinkedTreeMap> subsystemList = (ArrayList) enmListResponse.body();
    return subsystemList.stream()
        .map(x -> Integer.toString(((Double) x.get("id")).intValue()))
        .collect(Collectors.toSet());
  }

  public void deleteExistingSubsystems(Set<String> subsystemIds) throws IOException {
    // subsystemsDeleteApi.deleteSubsystems could delete all instances in one call, but retrofit
    // does not allow a request body in the DELETE request
    for (String id : subsystemIds) {
      Response<Void> deleteResponse = subsystemsDeleteApi.deleteSubsystem(id).execute();

      log.debug("Delete response code: {}", deleteResponse.code());
      log.debug("Delete response body: {}", deleteResponse.body());
    }
  }

  public void registerEnmInSubsystemManager(SimulatedEnm enm, String username, String password)
      throws IOException {
    Subsystem subsystem = new Subsystem();

    SubsystemType subsystemType = new SubsystemType();
    subsystemType.setType("DomainManager");
    subsystemType.setId(1L);
    subsystem.setSubsystemType(subsystemType);

    subsystem.setName(enm.getName());
    subsystem.setVendor("Ericsson");
    subsystem.setUrl(enm.getUri().toString());

    subsystem.setConnectionProperties(
        Collections.singletonList(buildConnectionProperties(username, password)));

    Response<Subsystem> createResponse =
        subsystemsCreationAndUpdateApi.postSubsystem(subsystem).execute();

    log.debug("Register ENM {} response code: {}", enm.getName(), createResponse.code());
    log.debug("Register ENM {} response body: {}", enm.getName(), createResponse.body());
  }

  private static ConnectionProperties buildConnectionProperties(String username, String password) {
    ConnectionProperties connectionProperties = new ConnectionProperties();
    connectionProperties.setUsername(username);
    connectionProperties.setPassword(password);

    List<String> encryptedKeys = new ArrayList<>();
    encryptedKeys.add("password");
    connectionProperties.setEncryptedKeys(encryptedKeys);

    return connectionProperties;
  }
}
