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

import com.ericsson.oss.adc.emsnc.client.cs.SubsystemsReadingApi;
import com.ericsson.oss.adc.emsnc.processing.data.EnmInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Service
@Slf4j
public class EmsInfoService {

  @Value("${emsnc.client.connected-systems.host}")
  private String connectedSystemsHost;

  @Value("${emsnc.client.connected-systems.port}")
  private int connectedSystemsPort;

  @SneakyThrows
  public List<EnmInfo> getCurrentListOfEnmInstances() {
    Retrofit connectedSystemsRetrofit = createRetrofit(getConnectedSystemsBaseUri().toString());
    SubsystemsReadingApi subsystemsReadingApi =
        connectedSystemsRetrofit.create(SubsystemsReadingApi.class);
    Response<Object> response =
        subsystemsReadingApi
            .getSubsystems(
                null, null, null, null, null, "{\"subsystemType\":\"DomainManager\"}", null, null)
            .execute();
    // TODO src/main/resources/subsystems-api.yaml spec file should be fixed, because the response
    // type here is an array not an object.
    List<EnmInfo> result = new ArrayList();
    List<Map<String, Object>> enmList = (List<Map<String, Object>>) response.body();
    for (Map enmDetails : enmList) {
      result.add(createEnmInfo(enmDetails));
    }

    return result;
  }

  private EnmInfo createEnmInfo(Map enmDetails) {

    return EnmInfo.builder()
        .name((String) enmDetails.get("name"))
        .enmUri(URI.create((String) enmDetails.get("url")))
        .enmUser(
            (String) ((Map) ((List) enmDetails.get("connectionProperties")).get(0)).get("username"))
        .enmPassword(
            (String) ((Map) ((List) enmDetails.get("connectionProperties")).get(0)).get("password"))
        .build();
  }

  private URI getConnectedSystemsBaseUri() {
    return URI.create(
        "http://" + connectedSystemsHost + ":" + connectedSystemsPort + "/subsystem-manager/v1/");
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
}
