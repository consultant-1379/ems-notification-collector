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
package com.ericsson.oss.adc.emsnc.client.enm;

import com.ericsson.oss.adc.emsnc.client.enm.model.EnmEventsResponse;
import com.ericsson.oss.adc.emsnc.client.enm.model.command.EnmCommandResponseData;
import java.util.Map;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.springframework.http.ResponseEntity;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public interface EnmClient {

  @POST(EnmClientConstants.LOGIN)
  Call<ResponseEntity<String>> login(@QueryMap Map<String, String> login);

  @GET(EnmClientConstants.LOGOUT)
  Call<Void> logout(@Header("Cookie") String cookie);

  @GET(EnmClientConstants.SERVER_CONFIG_EVENTS)
  Call<EnmEventsResponse> getConfigEvents(
      @Header("Cookie") String cookie,
      @Query("orderBy") String orderBy,
      @Query("filterClauses") String filterClauses);

  @POST(EnmClientConstants.SERVER_SCRIPTING_COMMAND)
  @Headers({
    "Accept-Encoding:gzip, deflate, sdch",
    "Accept:application/vnd.com.ericsson.oss.scripting+text;VERSION=\"1\"",
    "X-Requested-With:XMLHttpRequest"
  })
  @Multipart
  Call<ResponseBody> sendEnmCmCommand(
      @Header("Cookie") String cookie,
      @Part("name") RequestBody name,
      @Part("stream_output") RequestBody streamOutput,
      @Part("command") RequestBody command);

  @GET(
      EnmClientConstants.SERVER_SCRIPTING_GET
          + "{jobId}"
          + EnmClientConstants.SERVER_SCRIPTING_WAIT_SUFFIX)
  @Headers({
    "Accept-Encoding:gzip, deflate, sdch",
    "Accept:application/vnd.com.ericsson.oss.scripting.command+json;VERSION=\"2\"",
    "X-Requested-With:XMLHttpRequest"
  })
  Call<EnmCommandResponseData> getEnmCmCommandOutput(
      @Header("Cookie") String cookie, @Path("jobId") String jobId);
}
